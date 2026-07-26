package io.github.adamw7.tools.adopt;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import io.github.adamw7.tools.adopt.step.PullRequestOptions;

class CliArgumentsTest {

	private static final String REPO_URL = "https://github.com/owner/repo.git";
	private static final String OTHER_URL = "https://github.com/owner/other.git";
	private static final String THIRD_URL = "https://github.com/owner/third.git";

	@Test
	void parsesPositionalArguments() {
		CliArguments cli = CliArguments.parse(new String[] { REPO_URL, "/tmp/ws", "feature/x" });
		assertEquals(List.of(REPO_URL), cli.repositoryUrls());
		assertEquals(Path.of("/tmp/ws"), cli.workspace().orElseThrow());
		assertEquals("feature/x", cli.branchName());
	}

	@Test
	void collectsRepeatedRepositoryFlagsAfterThePositionalUrl() {
		CliArguments cli = CliArguments.parse(new String[] { REPO_URL, "--repo", OTHER_URL, "--repo", THIRD_URL });
		assertEquals(List.of(REPO_URL, OTHER_URL, THIRD_URL), cli.repositoryUrls());
	}

	@Test
	void adoptsTheFlaggedRepositoriesWhenNoPositionalUrlIsGiven() {
		CliArguments cli = CliArguments.parse(new String[] { "--repo", OTHER_URL, "--workspace", "/tmp/ws",
				"--branch", "feature/x" });
		assertEquals(List.of(OTHER_URL), cli.repositoryUrls());
		assertEquals(Path.of("/tmp/ws"), cli.workspace().orElseThrow());
		assertEquals("feature/x", cli.branchName());
	}

	@Test
	void readsARepositoryListFromAFile(@TempDir Path dir) throws IOException {
		Path list = Files.writeString(dir.resolve("repos.txt"),
				"# the team's repositories\n" + OTHER_URL + "\n\n  " + THIRD_URL + "  \n");
		CliArguments cli = CliArguments.parse(new String[] { REPO_URL, "--repos", list.toString() });
		assertEquals(List.of(REPO_URL, OTHER_URL, THIRD_URL), cli.repositoryUrls());
	}

	/**
	 * The same repository twice would clone into one checkout directory and adopt it
	 * a second time, so a URL listed in a file and named on the command line is one
	 * adoption, not two.
	 */
	@Test
	void adoptsARepositoryNamedTwiceOnlyOnce() {
		CliArguments cli = CliArguments.parse(new String[] { REPO_URL, "--repo", " " + REPO_URL + " " });
		assertEquals(List.of(REPO_URL), cli.repositoryUrls());
	}

	@Test
	void ignoresABlankRepositoryFlag() {
		CliArguments cli = CliArguments.parse(new String[] { REPO_URL, "--repo", "  " });
		assertEquals(List.of(REPO_URL), cli.repositoryUrls());
	}

	@Test
	void failsWhenTheRepositoryListFileCannotBeRead(@TempDir Path dir) {
		String missing = dir.resolve("absent.txt").toString();
		assertThrows(AdoptionException.class,
				() -> CliArguments.parse(new String[] { "--repos", missing }));
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
	void defaultsWorkspaceAndBranchWhenTheirFlagsAreBlank() {
		CliArguments cli = CliArguments.parse(new String[] { REPO_URL, "--workspace", "  ", "--branch", "  " });
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
	void parsesTheRuleVersionFlag() {
		CliArguments cli = CliArguments.parse(new String[] { REPO_URL, "--rule-version", "2.6.0" });
		assertEquals(Optional.of("2.6.0"), cli.ruleVersion());
	}

	@Test
	void noRuleVersionFlagLeavesTheRunningBuildsVersionToBeResolved() {
		assertEquals(Optional.empty(), CliArguments.parse(new String[] { REPO_URL }).ruleVersion());
	}

	/**
	 * A blank value counts as not supplied, the rule every other optional input
	 * follows, so it falls back to the running build's version rather than pinning an
	 * empty one the adopted project could not resolve.
	 */
	@Test
	void aBlankRuleVersionFallsBackToTheDefault() {
		CliArguments cli = CliArguments.parse(new String[] { REPO_URL, "--rule-version", "  " });
		assertEquals(Optional.empty(), cli.ruleVersion());
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
