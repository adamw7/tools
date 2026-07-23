package io.github.adamw7.tools.adopt.mcp;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.github.adamw7.tools.adopt.AdoptionContext;
import io.github.adamw7.tools.adopt.AdoptionReport;
import io.github.adamw7.tools.adopt.step.PullRequestOptions;
import io.github.adamw7.tools.mcp.ToolResult;

class AdoptToolTest {

	private static final String REPO_URL = "https://github.com/owner/repo.git";
	private static final String PR_URL = "https://github.com/owner/repo/pull/1";

	/** Records what the tool asked the pipeline to run and answers with a fixed report. */
	private static final class RecordingPipeline implements AdoptTool.Pipeline {

		private AdoptionContext context;
		private PullRequestOptions options;
		private boolean includeAssets;

		@Override
		public AdoptionReport adopt(AdoptionContext context, PullRequestOptions options, boolean includeAssets) {
			this.context = context;
			this.options = options;
			this.includeAssets = includeAssets;
			AdoptionReport report = new AdoptionReport();
			report.recordStep("pull-request");
			report.recordPullRequestUrl(PR_URL);
			return report;
		}
	}

	private final RecordingPipeline pipeline = new RecordingPipeline();
	private final AdoptTool tool = new AdoptTool(pipeline);

	@Test
	void definesTheAdoptRepoTool() {
		assertEquals("adopt_repo", tool.getToolDefinition().name());
		assertEquals(List.of("repository_url"), tool.getToolDefinition().inputSchema().get("required"));
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
	void ignoresBlankCommaSeparatedEntries() {
		tool.apply(Map.of("repository_url", REPO_URL, "reviewers", " , octocat ,, "));
		assertEquals(List.of("octocat"), pipeline.options.reviewers());
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
}
