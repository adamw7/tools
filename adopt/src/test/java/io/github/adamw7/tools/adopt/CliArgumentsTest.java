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
	private static final String FOURTH_URL = "https://github.com/owner/fourth.git";

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

	/**
	 * The list is read in the order the operator wrote it — the positional first,
	 * then each flag as it was met — so the run's report reads in the order they
	 * expect.
	 */
	@Test
	void keepsTheOrderTheRepositoriesWereNamedIn(@TempDir Path dir) throws IOException {
		Path list = Files.writeString(dir.resolve("repos.txt"), THIRD_URL + "\n");
		CliArguments cli = CliArguments.parse(new String[] { REPO_URL, "--repo", OTHER_URL, "--repos", list.toString(),
				"--repo", FOURTH_URL });
		assertEquals(List.of(REPO_URL, OTHER_URL, THIRD_URL, FOURTH_URL), cli.repositoryUrls());
	}

	@Test
	void adoptsARepositoryListedInAFileAndNamedOnTheCommandLineOnlyOnce(@TempDir Path dir) throws IOException {
		Path list = Files.writeString(dir.resolve("repos.txt"), REPO_URL + "\n" + OTHER_URL + "\n");
		CliArguments cli = CliArguments.parse(new String[] { REPO_URL, "--repos", list.toString() });
		assertEquals(List.of(REPO_URL, OTHER_URL), cli.repositoryUrls());
	}

	/**
	 * A file of nothing but comments names no repository, so a run with no other URL
	 * fails on its arguments rather than adopting nothing and reporting success.
	 */
	@Test
	void rejectsARepositoryListThatNamesNoRepository(@TempDir Path dir) throws IOException {
		Path list = Files.writeString(dir.resolve("repos.txt"), "# nothing to adopt yet\n\n");
		assertUsageFailure(new String[] { "--repos", list.toString() });
	}

	@Test
	void rejectsABlankRepositoryListFileName() {
		IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
				() -> CliArguments.parse(new String[] { REPO_URL, "--repos", "  " }));
		assertTrue(exception.getMessage().contains("--repos"), exception.getMessage());
	}

	@Test
	void rejectsTheRepositoryFlagsMissingTheirValue() {
		assertThrows(IllegalArgumentException.class, () -> CliArguments.parse(new String[] { REPO_URL, "--repo" }));
		assertThrows(IllegalArgumentException.class, () -> CliArguments.parse(new String[] { REPO_URL, "--repos" }));
	}

	/**
	 * The flags write the same value as their positionals, so an operator who names
	 * one twice gets the last one rather than an error they have to decode.
	 */
	@Test
	void theLastWorkspaceAndBranchNamedWins() {
		CliArguments cli = CliArguments.parse(new String[] { REPO_URL, "/tmp/ws", "feature/x",
				"--workspace", "/tmp/other", "--branch", "feature/y" });
		assertEquals(Path.of("/tmp/other"), cli.workspace().orElseThrow());
		assertEquals("feature/y", cli.branchName());
	}

	@Test
	void stripsSurroundingWhitespaceFromTheWorkspaceAndBranchFlags() {
		CliArguments cli = CliArguments.parse(new String[] { REPO_URL, "--workspace", " /tmp/ws ",
				"--branch", " feature/x " });
		assertEquals(Path.of("/tmp/ws"), cli.workspace().orElseThrow());
		assertEquals("feature/x", cli.branchName());
	}

	/** A positional after the branch is still a surplus argument, whatever the flags named. */
	@Test
	void rejectsExtraPositionalArgumentAlongsideTheRepositoryFlags() {
		assertThrows(IllegalArgumentException.class, () -> CliArguments
				.parse(new String[] { REPO_URL, "--repo", OTHER_URL, "/tmp/ws", "branch", "surplus" }));
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

	/**
	 * The workspace an operator meant to name positionally is read as a repository
	 * URL, so the run would clone a directory that is not a repository. Rejecting it
	 * names the flag that was meant instead of failing at the clone.
	 */
	@Test
	void rejectsAWorkspacePositionalAlongsideTheRepositoryFlags(@TempDir Path dir) throws IOException {
		Path list = Files.writeString(dir.resolve("repos.txt"), REPO_URL + "\n");
		IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
				() -> CliArguments.parse(new String[] { "/tmp/ws", "--repos", list.toString() }));
		assertTrue(exception.getMessage().contains("--workspace"), exception.getMessage());
		assertTrue(exception.getMessage().contains("/tmp/ws"), exception.getMessage());
	}

	@Test
	void rejectsAWorkspacePositionalWhateverOrderTheRepositoryFlagCameIn() {
		assertThrows(IllegalArgumentException.class,
				() -> CliArguments.parse(new String[] { "--repo", REPO_URL, "/tmp/ws" }));
	}

	/** A positional URL beside the flags is the documented way to name the first repository. */
	@Test
	void acceptsARepositoryPositionalAlongsideTheRepositoryFlags() {
		CliArguments cli = CliArguments.parse(new String[] { REPO_URL, "--repo", OTHER_URL });
		assertEquals(List.of(REPO_URL, OTHER_URL), cli.repositoryUrls());
	}

	/**
	 * A local path names no owner but is adoptable, so a run whose only repository is
	 * the positional keeps working — the check only fires when the flags named one too.
	 */
	@Test
	void acceptsALocalRepositoryPathAsTheOnlyRepository() {
		CliArguments cli = CliArguments.parse(new String[] { "/tmp/checkouts/repo", "/tmp/ws" });
		assertEquals(List.of("/tmp/checkouts/repo"), cli.repositoryUrls());
		assertEquals(Path.of("/tmp/ws"), cli.workspace().orElseThrow());
	}

	/** A blank repository flag names nothing, so it cannot turn the positional into a workspace. */
	@Test
	void aBlankRepositoryFlagDoesNotRejectALocalRepositoryPositional() {
		CliArguments cli = CliArguments.parse(new String[] { "/tmp/checkouts/repo", "--repo", "  " });
		assertEquals(List.of("/tmp/checkouts/repo"), cli.repositoryUrls());
	}

	private void assertUsageFailure(String[] args) {
		IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
				() -> CliArguments.parse(args));
		assertTrue(exception.getMessage().contains("Usage"), exception.getMessage());
	}
}
