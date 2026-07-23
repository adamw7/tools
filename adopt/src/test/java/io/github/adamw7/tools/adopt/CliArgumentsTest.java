package io.github.adamw7.tools.adopt;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;

import io.github.adamw7.tools.adopt.step.PullRequestOptions;

class CliArgumentsTest {

	private static final String REPO_URL = "https://github.com/owner/repo.git";

	@Test
	void parsesPositionalArguments() {
		CliArguments cli = CliArguments.parse(new String[] { REPO_URL, "/tmp/ws", "feature/x" });
		assertEquals(REPO_URL, cli.repositoryUrl());
		assertEquals(Path.of("/tmp/ws"), cli.workspace().orElseThrow());
		assertEquals("feature/x", cli.branchName());
	}

	@Test
	void defaultsWorkspaceAndBranchWhenOmitted() {
		CliArguments cli = CliArguments.parse(new String[] { REPO_URL });
		assertTrue(cli.workspace().isEmpty());
		assertEquals(AdoptionContext.DEFAULT_BRANCH, cli.branchName());
	}

	@Test
	void defaultsWorkspaceAndBranchWhenSuppliedBlank() {
		CliArguments cli = CliArguments.parse(new String[] { REPO_URL, "  ", "  " });
		assertTrue(cli.workspace().isEmpty());
		assertEquals(AdoptionContext.DEFAULT_BRANCH, cli.branchName());
	}

	@Test
	void defaultsPullRequestOptionsWhenNoFlagsGiven() {
		CliArguments cli = CliArguments.parse(new String[] { REPO_URL });
		assertEquals(PullRequestOptions.defaults(), cli.pullRequestOptions());
		assertFalse(cli.includeAssets());
		assertTrue(cli.reportFile().isEmpty());
	}

	@Test
	void parsesPullRequestMetadataFlags() {
		CliArguments cli = CliArguments.parse(new String[] { REPO_URL,
				"--title", "My title", "--body", "My body",
				"--reviewer", "octocat", "--reviewer", "hubot",
				"--label", "automation", "--assignee", "adamw7", "--draft" });
		PullRequestOptions options = cli.pullRequestOptions();
		assertEquals("My title", options.title());
		assertEquals("My body", options.body());
		assertEquals(List.of("octocat", "hubot"), options.reviewers());
		assertEquals(List.of("automation"), options.labels());
		assertEquals(List.of("adamw7"), options.assignees());
		assertTrue(options.draft());
	}

	@Test
	void parsesAssetsAndReportFlags() {
		CliArguments cli = CliArguments.parse(new String[] { REPO_URL, "--assets", "--report", "/tmp/report.json" });
		assertTrue(cli.includeAssets());
		assertEquals(Path.of("/tmp/report.json"), cli.reportFile().orElseThrow());
	}

	@Test
	void mixesFlagsAndPositionals() {
		CliArguments cli = CliArguments.parse(new String[] { REPO_URL, "--draft", "/tmp/ws", "feature/x" });
		assertEquals(Path.of("/tmp/ws"), cli.workspace().orElseThrow());
		assertEquals("feature/x", cli.branchName());
		assertTrue(cli.pullRequestOptions().draft());
	}

	@Test
	void rejectsNullArguments() {
		assertUsageFailure(null);
	}

	@Test
	void rejectsEmptyArguments() {
		assertUsageFailure(new String[0]);
	}

	@Test
	void rejectsBlankRepositoryUrl() {
		assertUsageFailure(new String[] { "   " });
	}

	@Test
	void rejectsUnknownOption() {
		IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
				() -> CliArguments.parse(new String[] { REPO_URL, "--frobnicate" }));
		assertTrue(exception.getMessage().contains("--frobnicate"), exception.getMessage());
	}

	@Test
	void rejectsFlagMissingItsValue() {
		IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
				() -> CliArguments.parse(new String[] { REPO_URL, "--title" }));
		assertTrue(exception.getMessage().contains("--title"), exception.getMessage());
	}

	@Test
	void rejectsExtraPositionalArgument() {
		assertThrows(IllegalArgumentException.class,
				() -> CliArguments.parse(new String[] { REPO_URL, "/tmp/ws", "branch", "surplus" }));
	}

	private void assertUsageFailure(String[] args) {
		IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
				() -> CliArguments.parse(args));
		assertTrue(exception.getMessage().contains("Usage"), exception.getMessage());
	}
}
