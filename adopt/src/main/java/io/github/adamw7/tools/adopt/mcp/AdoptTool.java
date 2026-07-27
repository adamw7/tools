package io.github.adamw7.tools.adopt.mcp;

import java.nio.file.Path;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import io.github.adamw7.tools.adopt.AdoptionContext;
import io.github.adamw7.tools.adopt.AdoptionReport;
import io.github.adamw7.tools.adopt.AdoptionReportWriter;
import io.github.adamw7.tools.adopt.AdoptionRun;
import io.github.adamw7.tools.adopt.BatchAdoption;
import io.github.adamw7.tools.adopt.Checkouts;
import io.github.adamw7.tools.adopt.GitHubRepoAdopter;
import io.github.adamw7.tools.adopt.RepositoryUrls;
import io.github.adamw7.tools.adopt.Workspaces;
import io.github.adamw7.tools.adopt.command.ProcessCommandRunner;
import io.github.adamw7.tools.adopt.step.PullRequestOptions;
import io.github.adamw7.tools.mcp.McpTool;
import io.github.adamw7.tools.mcp.ToolArguments;
import io.github.adamw7.tools.mcp.ToolDefinition;
import io.github.adamw7.tools.mcp.ToolResult;

/**
 * The MCP tool that runs the adoption pipeline: given one GitHub repository URL or
 * a list of them — plus optional workspace, branch, pull-request metadata, and the
 * starter-assets flag — it adopts Claude Code exactly as the command line does and
 * answers with the run's JSON {@link AdoptionReport}. The pipeline is injected
 * behind the {@link Pipeline} seam so tests exercise the argument mapping without
 * cloning anything.
 *
 * <p>A repository whose adoption fails does not strand the rest of the list: the
 * result carries every repository's report, marked as an error result when any of
 * them failed. A client that asked for five repositories needs to know which two
 * did not land, not only that something threw.
 */
public class AdoptTool implements McpTool {

	/**
	 * The adoption run the tool delegates to once the arguments are mapped, filling
	 * in the report it is handed so a failed repository still reports how far it got.
	 */
	public interface Pipeline {
		void adopt(AdoptionContext context, AdoptionReport report, PullRequestOptions options, boolean includeAssets,
				Optional<String> ruleVersion);
	}

	private static final Logger log = LogManager.getLogger(AdoptTool.class);

	private final Pipeline pipeline;
	private final AdoptionReportWriter reportWriter = new AdoptionReportWriter();

	private final ToolDefinition toolDefinition = new ToolDefinition("adopt_repo",
			"Adopt Claude Code into one or more GitHub repositories: clone each, create a feature branch, "
					+ "generate CLAUDE.md, wire a CLAUDE.md guard into the build, then push the branch and open a "
					+ "pull request. Name the repositories with repository_url, repository_urls, or both — at least "
					+ "one is required. Requires git, claude and gh on the server's PATH. "
					+ "Returns a JSON report with the pull request URL and the completed steps.",
			Map.of(
					"type", "object",
					"properties", Map.ofEntries(
							Map.entry("repository_url", Map.of("type", "string",
									"description", "URL of the GitHub repository to adopt")),
							Map.entry("repository_urls", Map.of("type", "array",
									"items", Map.of("type", "string"),
									"description", "URLs of the repositories to adopt, one after another; "
											+ "a comma-separated string is accepted too")),
							Map.entry("workspace", Map.of("type", "string",
									"description", "directory to clone into; a temporary one is created when omitted")),
							Map.entry("branch", Map.of("type", "string",
									"description", "feature branch to commit and open the pull request from")),
							Map.entry("title", Map.of("type", "string", "description", "pull request title")),
							Map.entry("body", Map.of("type", "string", "description", "pull request body")),
							Map.entry("reviewers", Map.of("type", "string",
									"description", "comma-separated reviewers to request")),
							Map.entry("labels", Map.of("type", "string",
									"description", "comma-separated labels to apply")),
							Map.entry("assignees", Map.of("type", "string",
									"description", "comma-separated assignees")),
							Map.entry("draft", Map.of("type", "boolean",
									"description", "open the pull request as a draft")),
							Map.entry("assets", Map.of("type", "boolean",
									"description", "also commit starter Claude Code configuration assets")),
							Map.entry("rule_version", Map.of("type", "string",
									"description", "released claude-code-enforcer version to wire into an adopted "
											+ "Maven project; defaults to the version of this build"))),
					"required", List.of()));

	public AdoptTool() {
		this(AdoptTool::runDefaultPipeline);
	}

	public AdoptTool(Pipeline pipeline) {
		this.pipeline = pipeline;
	}

	private static void runDefaultPipeline(AdoptionContext context, AdoptionReport report, PullRequestOptions options,
			boolean includeAssets, Optional<String> ruleVersion) {
		GitHubRepoAdopter.withDefaultPipeline(new ProcessCommandRunner(), options, includeAssets, ruleVersion)
				.adopt(context, report);
	}

	@Override
	public ToolDefinition getToolDefinition() {
		return toolDefinition;
	}

	@Override
	public ToolResult apply(Map<String, Object> arguments) {
		log.info("Calling MCP adopt tool for {}", arguments);
		PullRequestOptions options = optionsFrom(arguments);
		boolean includeAssets = ToolArguments.optionalBoolean(arguments, "assets", false);
		Optional<String> ruleVersion = ruleVersion(arguments);
		BatchAdoption batch = new BatchAdoption(
				(context, report) -> pipeline.adopt(context, report, options, includeAssets, ruleVersion));
		return result(batch.adoptAll(contextsFrom(arguments)));
	}

	/**
	 * A batch in which anything failed is an error result, so a client is not told
	 * an adoption that stopped at the push went through; the payload stays the report
	 * either way, since that is what says which repositories landed.
	 */
	private ToolResult result(List<AdoptionRun> runs) {
		String report = reportWriter.toJson(runs);
		return runs.stream().allMatch(AdoptionRun::succeeded) ? ToolResult.success(report) : ToolResult.error(report);
	}

	/** Every repository of a call is adopted into one workspace, on one branch name. */
	private List<AdoptionContext> contextsFrom(Map<String, Object> arguments) {
		return Checkouts.forRun(repositoryUrls(arguments), workspace(arguments), branch(arguments));
	}

	/**
	 * Reads the repositories from either argument, so a client with one repository
	 * keeps sending {@code repository_url} and one with a list sends
	 * {@code repository_urls}; supplying both simply adopts them all.
	 *
	 * @throws IllegalArgumentException when neither argument names a repository, the
	 *                                  one requirement the schema cannot express as a
	 *                                  required field
	 */
	private List<String> repositoryUrls(Map<String, Object> arguments) {
		List<String> urls = RepositoryUrls.distinct(Stream
				.concat(Stream.of(text(arguments, "repository_url")), textList(arguments, "repository_urls").stream())
				.toList());
		if (urls.isEmpty()) {
			throw new IllegalArgumentException("Missing required argument: repository_url or repository_urls");
		}
		return urls;
	}

	/**
	 * @return a list argument's entries, read from a real JSON array or from the
	 *         comma-separated string a loosely-typed client sends instead
	 */
	private List<String> textList(Map<String, Object> arguments, String key) {
		Object value = arguments.get(key);
		if (value instanceof Collection<?> entries) {
			return entries.stream().map(String::valueOf).toList();
		}
		return commaSeparated(arguments, key);
	}

	/**
	 * A blank {@code branch} argument falls back to the default branch, matching the
	 * command line's handling of a blank branch positional; only an explicitly named
	 * branch overrides it, so an empty string is not rejected as an invalid branch.
	 */
	private String branch(Map<String, Object> arguments) {
		return AdoptionContext.branchOrDefault(text(arguments, "branch"));
	}

	private Path workspace(Map<String, Object> arguments) {
		return Workspaces.resolveNamed(text(arguments, "workspace"));
	}

	private PullRequestOptions optionsFrom(Map<String, Object> arguments) {
		return new PullRequestOptions(text(arguments, "title"), text(arguments, "body"),
				commaSeparated(arguments, "reviewers"), commaSeparated(arguments, "labels"),
				commaSeparated(arguments, "assignees"), ToolArguments.optionalBoolean(arguments, "draft", false));
	}

	/**
	 * @return the rule version to pin, empty when the argument was not supplied or
	 *         was blank — the same rule every other optional argument follows
	 */
	private Optional<String> ruleVersion(Map<String, Object> arguments) {
		String supplied = text(arguments, "rule_version").strip();
		return supplied.isEmpty() ? Optional.empty() : Optional.of(supplied);
	}

	/** @return the argument's text, empty when it was not supplied */
	private String text(Map<String, Object> arguments, String key) {
		return ToolArguments.optionalString(arguments, key, "");
	}

	private List<String> commaSeparated(Map<String, Object> arguments, String key) {
		return Stream.of(text(arguments, key).split(",")).map(String::strip).filter(entry -> !entry.isEmpty()).toList();
	}
}
