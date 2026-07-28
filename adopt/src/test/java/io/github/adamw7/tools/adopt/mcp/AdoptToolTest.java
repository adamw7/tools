package io.github.adamw7.tools.adopt.mcp;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.github.adamw7.tools.adopt.AdoptionContext;
import io.github.adamw7.tools.adopt.AdoptionException;
import io.github.adamw7.tools.adopt.AdoptionReport;
import io.github.adamw7.tools.adopt.step.PullRequestOptions;
import io.github.adamw7.tools.mcp.ToolResult;

class AdoptToolTest {

	private static final String REPO_URL = "https://github.com/owner/repo.git";
	private static final String OTHER_URL = "https://github.com/owner/other.git";
	private static final String PR_URL = "https://github.com/owner/repo/pull/1";

	/** Records what the tool asked the pipeline to run and answers with a fixed report. */
	private static final class RecordingPipeline implements AdoptTool.Pipeline {

		private final List<AdoptionContext> contexts = new ArrayList<>();
		private final List<PullRequestOptions> optionsPerRepository = new ArrayList<>();
		private AdoptionContext context;
		private PullRequestOptions options;
		private boolean includeAssets;
		private Optional<String> ruleVersion = Optional.empty();

		@Override
		public void adopt(AdoptionContext context, AdoptionReport report, PullRequestOptions options,
				boolean includeAssets, Optional<String> ruleVersion) {
			this.contexts.add(context);
			this.optionsPerRepository.add(options);
			this.context = context;
			this.options = options;
			this.includeAssets = includeAssets;
			this.ruleVersion = ruleVersion;
			report.recordStep("pull-request");
			report.recordPullRequestUrl(PR_URL);
		}
	}

	private final RecordingPipeline pipeline = new RecordingPipeline();
	private final AdoptTool tool = new AdoptTool(pipeline);

	/**
	 * Neither URL argument is required on its own — either names the repositories —
	 * so the requirement is the one the tool checks itself rather than a schema field.
	 */
	@Test
	void definesTheAdoptRepoTool() {
		assertEquals("adopt_repo", tool.getToolDefinition().name());
		assertEquals(List.of(), tool.getToolDefinition().inputSchema().get("required"));
	}

	@Test
	void adoptsEveryRepositoryOfAList() {
		tool.apply(Map.of("repository_urls", List.of(REPO_URL, OTHER_URL)));
		assertEquals(List.of(REPO_URL, OTHER_URL),
				pipeline.contexts.stream().map(AdoptionContext::repositoryUrl).toList());
	}

	@Test
	void acceptsACommaSeparatedListOfRepositories() {
		tool.apply(Map.of("repository_urls", REPO_URL + " , " + OTHER_URL));
		assertEquals(List.of(REPO_URL, OTHER_URL),
				pipeline.contexts.stream().map(AdoptionContext::repositoryUrl).toList());
	}

	@Test
	void adoptsTheSingleUrlAndTheListTogetherWithoutRepeatingOne() {
		tool.apply(Map.of("repository_url", REPO_URL, "repository_urls", List.of(OTHER_URL, REPO_URL)));
		assertEquals(List.of(REPO_URL, OTHER_URL),
				pipeline.contexts.stream().map(AdoptionContext::repositoryUrl).toList());
	}

	@Test
	void adoptsEveryRepositoryOfAListIntoTheSameWorkspaceAndBranch(@TempDir Path dir) {
		tool.apply(Map.of("repository_urls", List.of(REPO_URL, OTHER_URL), "workspace", dir.toString(),
				"branch", "feature/x"));
		assertEquals(List.of(dir, dir), pipeline.contexts.stream().map(AdoptionContext::workspace).toList());
		assertEquals(List.of("feature/x", "feature/x"),
				pipeline.contexts.stream().map(AdoptionContext::branchName).toList());
	}

	@Test
	void answersWithAnEntryPerRepositoryOfABatch() throws IOException {
		ToolResult result = tool.apply(Map.of("repository_urls", List.of(REPO_URL, OTHER_URL)));
		assertFalse(result.isError());
		JsonNode node = new ObjectMapper().readTree(result.text());
		assertTrue(node.get("succeeded").asBoolean());
		assertEquals(2, node.get("repositories").size());
		assertEquals(OTHER_URL, node.get("repositories").get(1).get("repositoryUrl").asText());
	}

	/**
	 * A failed repository is answered with the report rather than an exception,
	 * because the report is what says which of the batch landed and which did not.
	 */
	@Test
	void answersWithAnErrorResultCarryingTheReportWhenARepositoryFails() throws IOException {
		AdoptTool failing = new AdoptTool((context, report, options, includeAssets, ruleVersion) -> {
			report.recordStep("clone");
			throw new AdoptionException("boom");
		});
		ToolResult result = failing.apply(Map.of("repository_url", REPO_URL));
		assertTrue(result.isError());
		JsonNode node = new ObjectMapper().readTree(result.text());
		assertFalse(node.get("succeeded").asBoolean());
		assertEquals("boom", node.get("failure").asText());
		assertEquals("clone", node.get("completedSteps").get(0).asText());
	}

	@Test
	void adoptsWithDefaultsWhenOnlyTheUrlIsGiven() {
		tool.apply(Map.of("repository_url", REPO_URL));
		assertEquals(REPO_URL, pipeline.context.repositoryUrl());
		assertEquals(AdoptionContext.DEFAULT_BRANCH, pipeline.context.branchName());
		assertEquals(PullRequestOptions.defaults(), pipeline.options);
		assertFalse(pipeline.includeAssets);
		assertTrue(Files.isDirectory(pipeline.context.workspace()));
	}

	@Test
	void usesTheSuppliedWorkspaceAndBranch(@TempDir Path dir) {
		Path workspace = dir.resolve("workspace");
		tool.apply(Map.of("repository_url", REPO_URL, "workspace", workspace.toString(), "branch", "feature/x"));
		assertEquals(workspace, pipeline.context.workspace());
		assertTrue(Files.isDirectory(workspace));
		assertEquals("feature/x", pipeline.context.branchName());
	}

	@Test
	void fallsBackToTheDefaultBranchWhenBlank() {
		tool.apply(Map.of("repository_url", REPO_URL, "branch", "  "));
		assertEquals(AdoptionContext.DEFAULT_BRANCH, pipeline.context.branchName());
	}

	@Test
	void mapsPullRequestMetadataOntoTheOptions() {
		tool.apply(Map.of("repository_url", REPO_URL,
				"title", "My title", "body", "My body",
				"reviewers", "octocat, hubot", "labels", "automation", "assignees", "adamw7",
				"draft", true, "assets", true));
		assertEquals("My title", pipeline.options.title());
		assertEquals("My body", pipeline.options.body());
		assertEquals(List.of("octocat", "hubot"), pipeline.options.reviewers());
		assertEquals(List.of("automation"), pipeline.options.labels());
		assertEquals(List.of("adamw7"), pipeline.options.assignees());
		assertTrue(pipeline.options.draft());
		assertTrue(pipeline.includeAssets);
	}

	@Test
	void passesTheRuleVersionOnToThePipeline() {
		tool.apply(Map.of("repository_url", REPO_URL, "rule_version", " 2.6.0 "));
		assertEquals(Optional.of("2.6.0"), pipeline.ruleVersion);
	}

	@Test
	void aBlankRuleVersionLeavesTheRunningBuildsVersionToBeResolved() {
		tool.apply(Map.of("repository_url", REPO_URL, "rule_version", "  "));
		assertEquals(Optional.empty(), pipeline.ruleVersion);
	}

	@Test
	void declaresTheRuleVersionArgument() {
		Object properties = tool.getToolDefinition().inputSchema().get("properties");
		assertTrue(properties instanceof Map<?, ?> declared && declared.containsKey("rule_version"),
				"the version must be settable through the tool: " + properties);
	}

	@Test
	void ignoresBlankCommaSeparatedEntries() {
		tool.apply(Map.of("repository_url", REPO_URL, "reviewers", " , octocat ,, "));
		assertEquals(List.of("octocat"), pipeline.options.reviewers());
	}

	/**
	 * A JSON array is the natural shape for a list, and the one this very tool takes
	 * for {@code repository_urls}. Read as plain text it became the list's own
	 * {@code toString()} split on its commas, and reached gh as
	 * {@code --reviewer "[octocat"} — failing the last step of an otherwise complete
	 * adoption.
	 */
	@Test
	void readsPullRequestListsSentAsJsonArrays() {
		tool.apply(Map.of("repository_url", REPO_URL,
				"reviewers", List.of("octocat", "hubot"),
				"labels", List.of("automation"),
				"assignees", List.of(" adamw7 ", "  ")));
		assertEquals(List.of("octocat", "hubot"), pipeline.options.reviewers());
		assertEquals(List.of("automation"), pipeline.options.labels());
		assertEquals(List.of("adamw7"), pipeline.options.assignees());
	}

	@Test
	void answersWithTheJsonReport() throws IOException {
		ToolResult result = tool.apply(Map.of("repository_url", REPO_URL));
		assertFalse(result.isError());
		JsonNode node = new ObjectMapper().readTree(result.text());
		assertEquals(REPO_URL, node.get("repositoryUrl").asText());
		assertEquals(PR_URL, node.get("pullRequestUrl").asText());
		assertEquals("pull-request", node.get("completedSteps").get(0).asText());
	}

	@Test
	void requiresTheRepositoryUrl() {
		assertThrows(IllegalArgumentException.class, () -> tool.apply(Map.of()));
	}

	/**
	 * The second of two repositories that would share a checkout is refused, so
	 * neither is adopted on the strength of the other's working tree. The refusal is
	 * that repository's own — the first is adopted as asked — and the call answers
	 * with an error result naming which of the two did not land.
	 */
	@Test
	void rejectsTheSecondOfTwoRepositoriesSharingACheckoutDirectory() {
		ToolResult result = tool.apply(Map.of("repository_urls",
				List.of("https://github.com/owner/tools.git", "https://github.com/other-owner/tools.git")));
		assertTrue(result.isError());
		assertEquals(List.of("https://github.com/owner/tools.git"),
				pipeline.contexts.stream().map(AdoptionContext::repositoryUrl).toList());
	}

	@Test
	void ignoresBlankEntriesInTheRepositoryList() {
		tool.apply(Map.of("repository_urls", List.of("  ", REPO_URL, "")));
		assertEquals(List.of(REPO_URL), pipeline.contexts.stream().map(AdoptionContext::repositoryUrl).toList());
	}

	/**
	 * A list of one is one repository's adoption, so it answers with the unwrapped
	 * document a client that never sends a list already parses.
	 */
	@Test
	void answersWithTheUnwrappedReportForAListOfOne() throws IOException {
		JsonNode node = new ObjectMapper().readTree(tool.apply(Map.of("repository_urls", List.of(REPO_URL))).text());
		assertEquals(REPO_URL, node.get("repositoryUrl").asText());
		assertEquals(PR_URL, node.get("pullRequestUrl").asText());
	}

	/**
	 * The repositories behind a failing one are adopted rather than stranded by it,
	 * and the batch's report says which was which.
	 */
	@Test
	void adoptsTheRestOfTheListAfterARepositoryFails() throws IOException {
		AdoptTool failing = new AdoptTool((context, report, options, includeAssets, ruleVersion) -> {
			if (REPO_URL.equals(context.repositoryUrl())) {
				throw new AdoptionException("boom");
			}
			report.recordPullRequestUrl(PR_URL);
		});
		ToolResult result = failing.apply(Map.of("repository_urls", List.of(REPO_URL, OTHER_URL)));
		assertTrue(result.isError());
		JsonNode node = new ObjectMapper().readTree(result.text());
		assertEquals("boom", node.get("repositories").get(0).get("failure").asText());
		assertTrue(node.get("repositories").get(1).get("succeeded").asBoolean());
		assertEquals(PR_URL, node.get("repositories").get(1).get("pullRequestUrl").asText());
	}

	/** The metadata is the call's, not the first repository's: every adoption gets it. */
	@Test
	void passesThePullRequestMetadataOnToEveryRepositoryOfAList() {
		tool.apply(Map.of("repository_urls", List.of(REPO_URL, OTHER_URL), "title", "My title", "assets", true));
		assertEquals(List.of("My title", "My title"),
				pipeline.optionsPerRepository.stream().map(PullRequestOptions::title).toList());
		assertTrue(pipeline.includeAssets);
	}

	@Test
	void requiresAtLeastOneRepositoryWhenBothArgumentsAreBlank() {
		assertThrows(IllegalArgumentException.class,
				() -> tool.apply(Map.of("repository_url", "  ", "repository_urls", " , ")));
	}

	@Test
	void declaresTheRepositoryListArgument() {
		Object properties = tool.getToolDefinition().inputSchema().get("properties");
		assertTrue(properties instanceof Map<?, ?> declared && declared.containsKey("repository_urls"),
				"a list of repositories must be settable through the tool: " + properties);
	}
}
