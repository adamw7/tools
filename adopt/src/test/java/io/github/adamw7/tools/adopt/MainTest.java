package io.github.adamw7.tools.adopt;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.github.adamw7.tools.adopt.command.CommandRunner;
import io.github.adamw7.tools.adopt.command.RecordingCommandRunner;
import io.github.adamw7.tools.adopt.step.AdoptionStep;

class MainTest {

	private static final String REPO_URL = "https://github.com/owner/repo.git";
	private static final String OTHER_URL = "https://github.com/owner/other.git";

	@Test
	void rejectsMissingArguments() {
		IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
				() -> Main.main(new String[0]));
		assertTrue(exception.getMessage().contains("Usage"), exception.getMessage());
	}

	@Test
	void rejectsNullArguments() {
		assertThrows(IllegalArgumentException.class, () -> Main.main(null));
	}

	@Test
	void rejectsBlankRepositoryUrl() {
		assertThrows(IllegalArgumentException.class, () -> Main.main(new String[] { "   " }));
	}

	@Test
	void rejectsUnknownOptionBeforeAnyWorkStarts() {
		assertThrows(IllegalArgumentException.class, () -> Main.main(new String[] { REPO_URL, "--frobnicate" }));
	}

	/**
	 * {@code --help} returns instead of adopting, so the flag can be asked for
	 * without a repository — and, when one is named beside it, without cloning that
	 * repository as a side effect of asking what the arguments are.
	 */
	@Test
	void answersHelpWithoutAdoptingAnything() {
		assertDoesNotThrow(() -> Main.main(new String[] { "--help" }));
		assertDoesNotThrow(() -> Main.main(new String[] { REPO_URL, "-h" }));
	}

	/**
	 * Every repository of a run shares the workspace and the branch name; only the
	 * checkout directory, derived from the repository name, differs.
	 */
	@Test
	void buildsAContextPerRepository(@TempDir Path dir) {
		CliArguments cli = CliArguments.parse(
				new String[] { REPO_URL, "--repo", OTHER_URL, "--workspace", dir.toString(), "--branch", "feature/x" });
		List<AdoptionContext> contexts = claimAll(cli);
		assertEquals(List.of(REPO_URL, OTHER_URL), contexts.stream().map(AdoptionContext::repositoryUrl).toList());
		assertEquals(List.of(dir, dir), contexts.stream().map(AdoptionContext::workspace).toList());
		assertEquals(List.of(dir.resolve("repo"), dir.resolve("other")),
				contexts.stream().map(AdoptionContext::repositoryDirectory).toList());
		assertEquals(List.of("feature/x", "feature/x"), contexts.stream().map(AdoptionContext::branchName).toList());
	}

	@Test
	void adoptsARepositoryNamedTwiceOnlyOnce(@TempDir Path dir) {
		CliArguments cli = CliArguments
				.parse(new String[] { REPO_URL, "--repo", REPO_URL, "--workspace", dir.toString() });
		assertEquals(List.of(REPO_URL), cli.repositoryUrls());
	}

	/**
	 * Two owners' repositories of the same name would clone into one checkout, so the
	 * second is refused — as its own failure, leaving the first adopted.
	 */
	@Test
	void rejectsARunWhoseRepositoriesShareACheckoutDirectory(@TempDir Path dir) {
		CliArguments cli = CliArguments.parse(new String[] { "https://github.com/owner/tools.git",
				"--repo", "https://github.com/other-owner/tools.git", "--workspace", dir.toString() });
		assertThrows(IllegalArgumentException.class, () -> claimAll(cli));
	}

	@Test
	void adoptsTheRepositoriesAListFileNames(@TempDir Path dir) throws IOException {
		Path list = Files.writeString(dir.resolve("repos.txt"), REPO_URL + "\n" + OTHER_URL + "\n");
		CliArguments cli = CliArguments
				.parse(new String[] { "--repos", list.toString(), "--workspace", dir.toString() });
		assertEquals(List.of(REPO_URL, OTHER_URL),
				claimAll(cli).stream().map(AdoptionContext::repositoryUrl).toList());
	}

	/**
	 * The workspace is resolved once for the run, so every repository is cloned under
	 * the same directory rather than each landing in a temporary one of its own.
	 */
	@Test
	void createsOneTemporaryWorkspaceForTheWholeBatch() {
		List<AdoptionContext> contexts = claimAll(CliArguments.parse(new String[] { REPO_URL, "--repo", OTHER_URL }));
		assertEquals(contexts.get(0).workspace(), contexts.get(1).workspace());
		assertTrue(Files.isDirectory(contexts.get(0).workspace()));
	}

	@Test
	void adoptsEveryRepositoryOfASucceedingBatch(@TempDir Path dir) throws IOException {
		Path file = dir.resolve("report.json");
		CliArguments cli = CliArguments.parse(new String[] { REPO_URL, "--repo", OTHER_URL,
				"--workspace", dir.toString(), "--report", file.toString() });
		List<AdoptionRun> runs = Main.runAndReport(cli, Main.checkouts(cli),
				new GitHubRepoAdopter(new RecordingCommandRunner(), List.of()));
		assertEquals(List.of(REPO_URL, OTHER_URL), runs.stream().map(AdoptionRun::repositoryUrl).toList());
		JsonNode node = new ObjectMapper().readTree(Files.readString(file));
		assertTrue(node.get("succeeded").asBoolean());
		assertEquals(2, node.get("repositories").size());
	}

	/**
	 * A run that stops part-way is the one whose report matters most — it records
	 * which steps completed and why the adoption stopped — so writing it only on the
	 * success path leaves nothing behind exactly when the operator needs it.
	 */
	@Test
	void writesTheReportWhenTheAdoptionFails(@TempDir Path dir) throws IOException {
		Path file = dir.resolve("report.json");
		AdoptionException thrown = assertThrows(AdoptionException.class,
				() -> Main.runAndReport(cli(dir, file), checkouts(dir), failingAdopter()));
		assertTrue(thrown.getMessage().contains(REPO_URL + ": explode: boom"), thrown.getMessage());
		JsonNode node = new ObjectMapper().readTree(Files.readString(file));
		assertFalse(node.get("succeeded").asBoolean());
		assertEquals("explode: boom", node.get("failure").asText());
	}

	@Test
	void writesTheReportWhenTheAdoptionSucceeds(@TempDir Path dir) throws IOException {
		Path file = dir.resolve("report.json");
		Main.runAndReport(cli(dir, file), checkouts(dir),
				new GitHubRepoAdopter(new RecordingCommandRunner(), List.of()));
		JsonNode node = new ObjectMapper().readTree(Files.readString(file));
		assertTrue(node.get("succeeded").asBoolean());
		assertTrue(node.get("failure").isNull());
	}

	/**
	 * The repositories behind a failing one are adopted rather than stranded by it,
	 * and the batch report says which was which — the whole point of taking a list.
	 */
	@Test
	void adoptsEveryRepositoryEvenWhenOneFails(@TempDir Path dir) throws IOException {
		Path file = dir.resolve("report.json");
		CliArguments cli = CliArguments.parse(new String[] { REPO_URL, "--repo", OTHER_URL,
				"--workspace", dir.toString(), "--report", file.toString() });
		assertThrows(AdoptionException.class,
				() -> Main.runAndReport(cli, Main.checkouts(cli), failingFor(REPO_URL)));
		JsonNode node = new ObjectMapper().readTree(Files.readString(file));
		assertFalse(node.get("succeeded").asBoolean());
		assertEquals(REPO_URL, node.get("repositories").get(0).get("repositoryUrl").asText());
		assertFalse(node.get("repositories").get(0).get("succeeded").asBoolean());
		assertTrue(node.get("repositories").get(1).get("succeeded").asBoolean());
	}

	@Test
	void reportsHowManyRepositoriesOfTheBatchFailed(@TempDir Path dir) {
		CliArguments cli = CliArguments.parse(new String[] { REPO_URL, "--repo", OTHER_URL, "--workspace",
				dir.toString() });
		AdoptionException thrown = assertThrows(AdoptionException.class,
				() -> Main.runAndReport(cli, Main.checkouts(cli), failingFor(REPO_URL)));
		assertTrue(thrown.getMessage().startsWith("Adoption failed for 1 of 2 repositories"), thrown.getMessage());
	}

	/**
	 * The adoption failure is the diagnostic the operator needs; an unwritable
	 * report file must be attached to it rather than thrown over it.
	 */
	@Test
	void anUnwritableReportDoesNotReplaceTheAdoptionFailure(@TempDir Path dir) throws IOException {
		Path blockingFile = Files.createFile(dir.resolve("not-a-directory"));
		AdoptionException thrown = assertThrows(AdoptionException.class, () -> Main
				.runAndReport(cli(dir, blockingFile.resolve("report.json")), checkouts(dir), failingAdopter()));
		assertTrue(thrown.getMessage().contains("explode: boom"), thrown.getMessage());
		assertEquals(1, thrown.getSuppressed().length);
	}

	@Test
	void writesNoReportWhenNoneWasRequested(@TempDir Path dir) {
		CliArguments cli = CliArguments.parse(new String[] { REPO_URL, dir.toString() });
		Main.runAndReport(cli, checkouts(dir), new GitHubRepoAdopter(new RecordingCommandRunner(), List.of()));
		assertTrue(cli.reportFile().isEmpty());
	}

	private CliArguments cli(Path workspace, Path reportFile) {
		return CliArguments.parse(new String[] { REPO_URL, workspace.toString(), "--report", reportFile.toString() });
	}

	private Checkouts checkouts(Path workspace) {
		return new Checkouts(workspace, AdoptionContext.DEFAULT_BRANCH);
	}

	/** The contexts a run would claim, in order, as the batch claims them one at a time. */
	private List<AdoptionContext> claimAll(CliArguments cli) {
		Checkouts checkouts = Main.checkouts(cli);
		return cli.repositoryUrls().stream().map(checkouts::claim).toList();
	}

	private GitHubRepoAdopter failingAdopter() {
		return failingFor(REPO_URL);
	}

	/** An adopter whose only step explodes for the named repository and passes for the rest. */
	private GitHubRepoAdopter failingFor(String repositoryUrl) {
		return new GitHubRepoAdopter(new RecordingCommandRunner(), List.of(new AdoptionStep() {

			@Override
			public String name() {
				return "explode";
			}

			@Override
			public void execute(AdoptionContext context, CommandRunner runner) {
				if (repositoryUrl.equals(context.repositoryUrl())) {
					throw new AdoptionException("boom");
				}
			}
		}));
	}
}
