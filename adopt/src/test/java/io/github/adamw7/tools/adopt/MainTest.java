package io.github.adamw7.tools.adopt;

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

	@Test
	void createsSuppliedWorkspaceDirectoryWhenMissing(@TempDir Path dir) {
		Path workspace = dir.resolve("nested/workspace");
		Path resolved = Main.workspace(CliArguments.parse(new String[] { REPO_URL, workspace.toString() }));
		assertEquals(workspace, resolved);
		assertTrue(Files.isDirectory(workspace));
	}

	@Test
	void keepsAnExistingSuppliedWorkspaceDirectory(@TempDir Path dir) {
		Path resolved = Main.workspace(CliArguments.parse(new String[] { REPO_URL, dir.toString() }));
		assertEquals(dir, resolved);
		assertTrue(Files.isDirectory(dir));
	}

	@Test
	void createsTemporaryWorkspaceWhenNoneSupplied() {
		Path resolved = Main.workspace(CliArguments.parse(new String[] { REPO_URL }));
		assertTrue(Files.isDirectory(resolved));
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
				() -> Main.runAndReport(cli(dir, file), context(dir), failingAdopter()));
		assertEquals("boom", thrown.getMessage());
		JsonNode node = new ObjectMapper().readTree(Files.readString(file));
		assertFalse(node.get("succeeded").asBoolean());
		assertEquals("explode: boom", node.get("failure").asText());
	}

	@Test
	void writesTheReportWhenTheAdoptionSucceeds(@TempDir Path dir) throws IOException {
		Path file = dir.resolve("report.json");
		Main.runAndReport(cli(dir, file), context(dir), new GitHubRepoAdopter(new RecordingCommandRunner(), List.of()));
		JsonNode node = new ObjectMapper().readTree(Files.readString(file));
		assertTrue(node.get("succeeded").asBoolean());
		assertTrue(node.get("failure").isNull());
	}

	/**
	 * The adoption failure is the diagnostic the operator needs; an unwritable
	 * report file must be attached to it rather than thrown over it.
	 */
	@Test
	void anUnwritableReportDoesNotReplaceTheAdoptionFailure(@TempDir Path dir) throws IOException {
		Path blockingFile = Files.createFile(dir.resolve("not-a-directory"));
		AdoptionException thrown = assertThrows(AdoptionException.class, () -> Main
				.runAndReport(cli(dir, blockingFile.resolve("report.json")), context(dir), failingAdopter()));
		assertEquals("boom", thrown.getMessage());
		assertEquals(1, thrown.getSuppressed().length);
	}

	@Test
	void writesNoReportWhenNoneWasRequested(@TempDir Path dir) {
		CliArguments cli = CliArguments.parse(new String[] { REPO_URL, dir.toString() });
		Main.runAndReport(cli, context(dir), new GitHubRepoAdopter(new RecordingCommandRunner(), List.of()));
		assertTrue(cli.reportFile().isEmpty());
	}

	private CliArguments cli(Path workspace, Path reportFile) {
		return CliArguments.parse(new String[] { REPO_URL, workspace.toString(), "--report", reportFile.toString() });
	}

	private AdoptionContext context(Path workspace) {
		return new AdoptionContext(REPO_URL, workspace);
	}

	private GitHubRepoAdopter failingAdopter() {
		return new GitHubRepoAdopter(new RecordingCommandRunner(), List.of(new AdoptionStep() {

			@Override
			public String name() {
				return "explode";
			}

			@Override
			public void execute(AdoptionContext context, CommandRunner runner) {
				throw new AdoptionException("boom");
			}
		}));
	}
}
