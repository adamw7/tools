package io.github.adamw7.tools.adopt.mcp;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.stream.Stream;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import io.github.adamw7.tools.adopt.AdoptionContext;
import io.github.adamw7.tools.adopt.AdoptionReport;
import io.github.adamw7.tools.adopt.AdoptionReportWriter;
import io.github.adamw7.tools.adopt.GitHubRepoAdopter;
import io.github.adamw7.tools.adopt.Workspaces;
import io.github.adamw7.tools.adopt.command.ProcessCommandRunner;
import io.github.adamw7.tools.adopt.step.PullRequestOptions;
import io.github.adamw7.tools.mcp.McpTool;
import io.github.adamw7.tools.mcp.ToolArguments;
import io.github.adamw7.tools.mcp.ToolDefinition;
import io.github.adamw7.tools.mcp.ToolResult;

/**
 * The MCP tool that runs the adoption pipeline: given a GitHub repository URL —
 * plus optional workspace, branch, pull-request metadata, and the flag for the
 * starter-assets step — it adopts Claude Code into the repository exactly as the
 * command line does and answers with the run's JSON {@link AdoptionReport},
 * including the opened pull request's URL. The pipeline itself is injected
 * behind the {@link Pipeline} seam, so tests exercise the argument mapping and
 * the result shape without cloning anything; the default wiring runs the real
 * default pipeline against a {@link ProcessCommandRunner}.
 */
public class AdoptTool implements McpTool {

	/** The adoption run the tool delegates to once the arguments are mapped. */
	public interface Pipeline {
		AdoptionReport adopt(AdoptionContext context, PullRequestOptions options, boolean includeAssets);
	}

	private static final Logger log = LogManager.getLogger(AdoptTool.class);

	private final Pipeline pipeline;
	private final AdoptionReportWriter reportWriter = new AdoptionReportWriter();

	private final ToolDefinition toolDefinition = new ToolDefinition("adopt_repo",
			"Adopt Claude Code into a GitHub repository: clone it, create a feature branch, generate CLAUDE.md, "
					+ "wire a CLAUDE.md guard into the build, then push the branch and open a pull request. "
					+ "Requires git, claude and gh on the server's PATH. "
					+ "Returns a JSON report with the pull request URL and the completed steps.",
			Map.of(
					"type", "object",
					"properties", Map.of(
							"repository_url", Map.of("type", "string",
									"description", "URL of the GitHub repository to adopt"),
							"workspace", Map.of("type", "string",
									"description", "directory to clone into; a temporary one is created when omitted"),
							"branch", Map.of("type", "string",
									"description", "feature branch to commit and open the pull request from"),
							"title", Map.of("type", "string", "description", "pull request title"),
							"body", Map.of("type", "string", "description", "pull request body"),
							"reviewers", Map.of("type", "string",
									"description", "comma-separated reviewers to request"),
							"labels", Map.of("type", "string", "description", "comma-separated labels to apply"),
							"assignees", Map.of("type", "string", "description", "comma-separated assignees"),
							"draft", Map.of("type", "boolean", "description", "open the pull request as a draft"),
							"assets", Map.of("type", "boolean",
									"description", "also commit starter Claude Code configuration assets")),
					"required", List.of("repository_url")));

	public AdoptTool() {
		this(AdoptTool::runDefaultPipeline);
	}

	public AdoptTool(Pipeline pipeline) {
		this.pipeline = pipeline;
	}

	private static AdoptionReport runDefaultPipeline(AdoptionContext context, PullRequestOptions options,
			boolean includeAssets) {
		return GitHubRepoAdopter.withDefaultPipeline(new ProcessCommandRunner(), options, includeAssets)
				.adopt(context);
	}

	@Override
	public ToolDefinition getToolDefinition() {
		return toolDefinition;
	}

	@Override
	public ToolResult apply(Map<String, Object> arguments) {
		log.info("Calling MCP adopt tool for {}", arguments);
		AdoptionContext context = contextFrom(arguments);
		AdoptionReport report = pipeline.adopt(context, optionsFrom(arguments),
				ToolArguments.optionalBoolean(arguments, "assets", false));
		return ToolResult.success(reportWriter.toJson(context, report));
	}

	private AdoptionContext contextFrom(Map<String, Object> arguments) {
		String repositoryUrl = ToolArguments.requiredString(arguments, "repository_url");
		return new AdoptionContext(repositoryUrl, workspace(arguments), branch(arguments));
	}

	/**
	 * A blank {@code branch} argument falls back to the default branch, matching the
	 * command line's handling of a blank branch positional; only an explicitly named
	 * branch overrides it, so an empty string is not rejected as an invalid branch.
	 */
	private String branch(Map<String, Object> arguments) {
		String branch = ToolArguments.optionalString(arguments, "branch", AdoptionContext.DEFAULT_BRANCH);
		return branch.isBlank() ? AdoptionContext.DEFAULT_BRANCH : branch;
	}

	private Path workspace(Map<String, Object> arguments) {
		String workspace = ToolArguments.optionalString(arguments, "workspace", "");
		return workspace.isBlank() ? Workspaces.createTemporary() : Workspaces.createIfMissing(Path.of(workspace));
	}

	private PullRequestOptions optionsFrom(Map<String, Object> arguments) {
		PullRequestOptions.Builder builder = PullRequestOptions.builder()
				.reviewers(commaSeparated(arguments, "reviewers"))
				.labels(commaSeparated(arguments, "labels"))
				.assignees(commaSeparated(arguments, "assignees"))
				.draft(ToolArguments.optionalBoolean(arguments, "draft", false));
		applyText(arguments, "title", builder::title);
		applyText(arguments, "body", builder::body);
		return builder.build();
	}

	private void applyText(Map<String, Object> arguments, String key, Consumer<String> target) {
		String value = ToolArguments.optionalString(arguments, key, "");
		if (!value.isBlank()) {
			target.accept(value);
		}
	}

	private List<String> commaSeparated(Map<String, Object> arguments, String key) {
		String value = ToolArguments.optionalString(arguments, key, "");
		return Stream.of(value.split(",")).map(String::strip).filter(entry -> !entry.isEmpty()).toList();
	}
}
