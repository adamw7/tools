package io.github.adamw7.tools.adopt.mcp;

import java.nio.file.Path;
import java.time.Duration;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import io.github.adamw7.tools.adopt.AdoptionContext;
import io.github.adamw7.tools.adopt.AdoptionOptions;
import io.github.adamw7.tools.adopt.step.GuardRules;
import io.github.adamw7.tools.adopt.AdoptionReport;
import io.github.adamw7.tools.adopt.AdoptionReportWriter;
import io.github.adamw7.tools.adopt.AdoptionRun;
import io.github.adamw7.tools.adopt.BatchAdoption;
import io.github.adamw7.tools.adopt.CheckoutRetention;
import io.github.adamw7.tools.adopt.Checkouts;
import io.github.adamw7.tools.adopt.GitHubRepoAdopter;
import io.github.adamw7.tools.adopt.Redaction;
import io.github.adamw7.tools.adopt.RepositoryUrls;
import io.github.adamw7.tools.adopt.Workspaces;
import io.github.adamw7.tools.adopt.command.CommandRunners;
import io.github.adamw7.tools.adopt.command.ProcessCommandRunner;
import io.github.adamw7.tools.adopt.step.PullRequestOptions;
import io.github.adamw7.tools.mcp.McpTool;
import io.github.adamw7.tools.mcp.ToolArguments;
import io.github.adamw7.tools.mcp.ToolDefinition;
import io.github.adamw7.tools.mcp.ToolResult;

/**
 * The MCP tool that runs the adoption pipeline: given one GitHub repository URL or
 * a list of them — plus the optional workspace, branch, and {@link AdoptionOptions}
 * the call configures the run with — it adopts Claude Code exactly as the command
 * line does and answers with the run's JSON {@link AdoptionReport}. The pipeline is
 * injected behind the {@link Pipeline} seam so tests exercise the argument mapping
 * without cloning anything.
 *
 * <p>A {@code dry_run} call is the one an agent should reach for first: it clones,
 * branches, and commits into the workspace, but pushes nothing and opens no pull
 * request, so what the adoption would do can be read off the report and the
 * checkout before any of it reaches GitHub.
 *
 * <p>A repository whose adoption fails does not strand the rest of the list: the
 * result carries every repository's report, marked as an error result when any of
 * them failed. A client that asked for five repositories needs to know which two
 * did not land, not only that something threw.
 */
public class AdoptTool implements McpTool {

	/**
	 * Builds the adoption a call runs, from the arguments that call supplied. The
	 * seam is a factory rather than the adoption itself so the pipeline — and the
	 * command runner behind it — is assembled once and then adopts every repository
	 * of the batch, exactly as the command line does.
	 */
	public interface Pipeline {
		BatchAdoption.Adoption create(AdoptionOptions options);
	}

	private static final Logger log = LogManager.getLogger(AdoptTool.class);

	private static final int DEFAULT_TIMEOUT_MINUTES = (int) ProcessCommandRunner.DEFAULT_TIMEOUT.toMinutes();

	/** {@link AdoptionOptions#MAX_TIMEOUT} in the units this argument is written in. */
	private static final int MAX_TIMEOUT_MINUTES = (int) AdoptionOptions.MAX_TIMEOUT.toMinutes();

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
									"description", "comma-separated reviewers to request; an array is accepted too")),
							Map.entry("labels", Map.of("type", "string",
									"description", "comma-separated labels to apply; an array is accepted too")),
							Map.entry("assignees", Map.of("type", "string",
									"description", "comma-separated assignees; an array is accepted too")),
							Map.entry("draft", Map.of("type", "boolean",
									"description", "open the pull request as a draft")),
							Map.entry("assets", Map.of("type", "boolean",
									"description", "also commit starter Claude Code configuration assets")),
							Map.entry("rule_version", Map.of("type", "string",
									"description", "released claude-code-enforcer version to wire into an adopted "
											+ "Maven project; defaults to the version of this build")),
							Map.entry("keep_workspace", Map.of("type", "boolean",
									"description", "keep every checkout after a successful adoption; by default "
											+ "a checkout whose adoption landed is removed, since its product is "
											+ "the pushed branch and the pull request")),
							Map.entry("dry_run", Map.of("type", "boolean",
									"description", "rehearse the adoption: clone, branch, and commit in the "
											+ "workspace, but push nothing and open no pull request")),
							Map.entry("timeout_minutes", Map.of("type", "integer",
									"description", "how long any one git/claude/gh/build command may run before it "
											+ "is killed; defaults to " + DEFAULT_TIMEOUT_MINUTES)),
							Map.entry("retries", Map.of("type", "integer",
									"description", "how many further attempts a git or gh command the network "
											+ "refused earns, waiting longer before each; defaults to "
											+ AdoptionOptions.DEFAULT_RETRIES + ", and 0 reports the first failure")),
							Map.entry("rules", Map.of("type", "string",
									"description", "how much of the adopted repository's Claude Code configuration "
											+ "the wired guard checks: 'project' (default) for all of it, or "
											+ "'minimal' for the CLAUDE.md format alone")),
							Map.entry("claude_md_sections", Map.of("type", "string",
									"description", "comma-separated CLAUDE.md headings the guard demands and the "
											+ "reshape conforms to; an array is accepted too. Defaults to what the "
											+ "detected build system asks for"))),
					"required", List.of()));

	public AdoptTool() {
		this(AdoptTool::runDefaultPipeline);
	}

	public AdoptTool(Pipeline pipeline) {
		this.pipeline = pipeline;
	}

	/**
	 * One runner adopts every repository of the call, assembled by the same
	 * {@link CommandRunners} the command line uses so the two cannot answer a
	 * {@code timeout_minutes} or a {@code retries} differently.
	 */
	private static BatchAdoption.Adoption runDefaultPipeline(AdoptionOptions options) {
		return GitHubRepoAdopter.withDefaultPipeline(CommandRunners.forRun(options), options)::adopt;
	}

	@Override
	public ToolDefinition getToolDefinition() {
		return toolDefinition;
	}

	@Override
	public ToolResult apply(Map<String, Object> arguments) {
		log.info("Calling MCP adopt tool for {}", describe(arguments));
		AdoptionOptions options = adoptionOptionsFrom(arguments);
		BatchAdoption batch = new BatchAdoption(pipeline.create(options), checkoutRetention(arguments, options));
		return result(batch.adoptAll(repositoryUrls(arguments), checkoutsFrom(arguments)));
	}

	/**
	 * What becomes of each repository's checkout, decided the same way the command
	 * line decides it. A server serving many calls accumulates clones faster than an
	 * operator does, so the default matters more here, not less.
	 */
	private CheckoutRetention checkoutRetention(Map<String, Object> arguments, AdoptionOptions options) {
		return CheckoutRetention.of(ToolArguments.optionalBoolean(arguments, "keep_workspace", false),
				options.dryRun());
	}

	/**
	 * The call's arguments as they may be logged. A {@code repository_url} is the one
	 * argument a client supplies with credentials in it, and this server is
	 * long-lived, so logging the map as it arrived wrote that token to the log of
	 * every call. The pipeline redacts the URL from its own logs; the arguments have
	 * to be redacted here, before they reach it.
	 */
	static String describe(Map<String, Object> arguments) {
		return Redaction.of(String.valueOf(arguments));
	}

	/**
	 * A batch in which anything failed is an error result, so a client is not told
	 * an adoption that stopped at the push went through; the payload stays the report
	 * either way, since that is what says which repositories landed.
	 */
	private ToolResult result(List<AdoptionRun> runs) {
		String report = reportWriter.toJson(runs);
		return AdoptionRun.allSucceeded(runs) ? ToolResult.success(report) : ToolResult.error(report);
	}

	/** Every repository of a call is adopted into one workspace, on one branch name. */
	private Checkouts checkoutsFrom(Map<String, Object> arguments) {
		return new Checkouts(workspace(arguments), branch(arguments));
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
	 *         comma-separated string a loosely-typed client sends instead. Both
	 *         shapes are stripped and their blank entries dropped, so which one the
	 *         client chose cannot change the list the pipeline is handed.
	 */
	private List<String> textList(Map<String, Object> arguments, String key) {
		Object value = arguments.get(key);
		if (value instanceof Collection<?> entries) {
			return texts(entries.stream().map(String::valueOf));
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

	/**
	 * The three list arguments are read through {@link #textList} rather than as
	 * plain text, because a client that sends a JSON array would otherwise have the
	 * list's {@code toString()} split on its commas and reach {@code gh} as
	 * {@code --reviewer "[octocat"}, failing the last step of a complete adoption.
	 */
	private PullRequestOptions pullRequestOptionsFrom(Map<String, Object> arguments) {
		return new PullRequestOptions(text(arguments, "title"), text(arguments, "body"),
				textList(arguments, "reviewers"), textList(arguments, "labels"),
				textList(arguments, "assignees"), ToolArguments.optionalBoolean(arguments, "draft", false));
	}

	/**
	 * The call's whole configuration, in the shape the command line builds too, so
	 * the two entry points cannot drift apart on what an omitted argument means. A
	 * blank {@code rule_version} is normalised to "not supplied" by
	 * {@link AdoptionOptions} itself, as every other optional argument is.
	 */
	private AdoptionOptions adoptionOptionsFrom(Map<String, Object> arguments) {
		return new AdoptionOptions(pullRequestOptionsFrom(arguments),
				ToolArguments.optionalBoolean(arguments, "assets", false), text(arguments, "rule_version"),
				ToolArguments.optionalBoolean(arguments, "dry_run", false), commandTimeout(arguments),
				retries(arguments), guardRules(arguments), textList(arguments, "claude_md_sections"));
	}

	/**
	 * Refused here rather than defaulted, the same as on the command line: a client
	 * naming a rule set that does not exist has asked for something, and quietly
	 * giving it the default would change what the adopted build enforces without
	 * saying so.
	 */
	private GuardRules guardRules(Map<String, Object> arguments) {
		String rules = text(arguments, "rules");
		return rules == null || rules.isBlank() ? GuardRules.PROJECT : GuardRules.of(rules);
	}

	/**
	 * Bounded by {@link AdoptionOptions#MAX_RETRIES} here as well as there, so a client
	 * asking for a hundred attempts is refused with the argument's name rather than
	 * with the record's complaint about a field it never sent.
	 */
	private int retries(Map<String, Object> arguments) {
		return ToolArguments.optionalBoundedInt(arguments, "retries", AdoptionOptions.DEFAULT_RETRIES, 0,
				AdoptionOptions.MAX_RETRIES);
	}

	/**
	 * Bounded by {@link AdoptionOptions#MAX_TIMEOUT} here as well as there, so a
	 * client asking for a week-long timeout — which this long-lived server would hand
	 * a command it can never reclaim — is refused with the argument's name rather than
	 * with the record's complaint about a field it never sent.
	 */
	private Duration commandTimeout(Map<String, Object> arguments) {
		return Duration.ofMinutes(ToolArguments.optionalBoundedInt(arguments, "timeout_minutes",
				DEFAULT_TIMEOUT_MINUTES, 1, MAX_TIMEOUT_MINUTES));
	}

	/** @return the argument's text, empty when it was not supplied */
	private String text(Map<String, Object> arguments, String key) {
		return ToolArguments.optionalString(arguments, key, "");
	}

	private List<String> commaSeparated(Map<String, Object> arguments, String key) {
		return texts(Stream.of(text(arguments, key).split(",")));
	}

	private List<String> texts(Stream<String> entries) {
		return entries.map(String::strip).filter(entry -> !entry.isEmpty()).toList();
	}
}
