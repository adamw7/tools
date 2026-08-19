package io.github.adamw7.tools.enforcer.cli;

import static io.github.adamw7.tools.test.TestFiles.createDirectory;
import static io.github.adamw7.tools.test.TestFiles.writeString;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * The command line as a pre-commit hook or a non-Maven project meets it: run it at
 * a directory and read what it says.
 */
class MainTest {

	private static final String VALID_CLAUDE_MD = """
			# CLAUDE.md

			See [AGENTS.md](AGENTS.md) for the full agent guide.

			## Project
			Java project built with Maven.

			## Java version
			Java 25.

			## Maven
			Versions live in the root pom.

			## Principles for Java Development
			Use SOLID Principles.

			## Testing
			Write unit tests for all new logic.

			## Dependencies
			Ask before adding a new one.
			""";

	@TempDir
	private Path tempDir;

	private ByteArrayOutputStream out;
	private ByteArrayOutputStream err;

	@BeforeEach
	void captureTheStreams() {
		out = new ByteArrayOutputStream();
		err = new ByteArrayOutputStream();
	}

	@AfterEach
	void clearTheReportDirectory() {
		System.clearProperty("claude.enforcer.reportDir");
	}

	@Test
	void reportsAValidConfigurationAndSaysSo() {
		writeString(tempDir.resolve("CLAUDE.md"), VALID_CLAUDE_MD);

		assertDoesNotThrow(() -> run(tempDir.toString()));

		assertTrue(output().contains("Claude Code configuration is valid."), output());
	}

	/** The whole point: no Maven, no bootstrap, no pom edit. */
	@Test
	void printsTheViolationsAndThenFails() {
		writeString(tempDir.resolve("CLAUDE.md"), "# CLAUDE.md\n\nNothing else.\n");

		assertThrows(Main.CheckFailedException.class, () -> run(tempDir.toString()));

		assertTrue(errors().contains("[claudeMdFormat]"), errors());
	}

	/** A hook shows its user the report, not the throw, so the report comes first. */
	@Test
	void failureNamesTheOutcomeRatherThanRepeatingTheReport() {
		writeString(tempDir.resolve("CLAUDE.md"), "# CLAUDE.md\n\nNothing else.\n");

		Main.CheckFailedException thrown = assertThrows(Main.CheckFailedException.class,
				() -> run(tempDir.toString()));

		assertTrue(thrown.getMessage().contains("not valid"), thrown.getMessage());
		assertFalse(thrown.getMessage().contains("[claudeMdFormat]"), thrown.getMessage());
	}

	@Test
	void repairsWhatItCanWhenAskedTo() {
		Path claudeMd = writeString(tempDir.resolve("CLAUDE.md"), "## Project purpose\nA project.\n");

		assertDoesNotThrow(() -> run(tempDir.toString(), "--fix"));

		assertTrue(readString(claudeMd).startsWith("# CLAUDE.md"), readString(claudeMd));
	}

	@Test
	void skipsTheRulesItIsToldTo() {
		writeString(tempDir.resolve("CLAUDE.md"), "# CLAUDE.md\n\nNothing else.\n");

		assertDoesNotThrow(() -> run(tempDir.toString(), "--skip",
				"claudeMdFormat,memoryImports,contextBudget"));
	}

	@Test
	void reportsWithoutFailingWhenAskedToWarn() {
		writeString(tempDir.resolve("CLAUDE.md"), "# CLAUDE.md\n\nNothing else.\n");

		assertDoesNotThrow(() -> run(tempDir.toString(), "--warn"));

		assertTrue(errors().contains("[claudeMdFormat]"), errors());
	}

	@Test
	void writesTheReportsWhereItIsToldTo() {
		writeString(tempDir.resolve("CLAUDE.md"), VALID_CLAUDE_MD);
		Path reports = createDirectory(tempDir.resolve("reports"));

		assertDoesNotThrow(() -> run(tempDir.toString(), "--report", reports.toString()));

		assertTrue(Files.exists(reports.resolve("index.html")));
		assertTrue(Files.exists(reports.resolve("claudeCodeProject.html")));
	}

	/**
	 * One verdict, one report. The parts hand their violations to the composite rather
	 * than reporting themselves, so a run leaves the whole configuration's outcome in
	 * one page instead of twenty pages that each answer for a fragment of it.
	 */
	@Test
	void writesOneReportForTheWholeConfiguration() {
		writeString(tempDir.resolve("CLAUDE.md"), "# CLAUDE.md\n\nNothing else.\n");
		Path reports = createDirectory(tempDir.resolve("reports"));

		assertThrows(Main.CheckFailedException.class,
				() -> run(tempDir.toString(), "--report", reports.toString()));

		assertFalse(Files.exists(reports.resolve("claudeMdFormat.html")));
		assertTrue(readString(reports.resolve("claudeCodeProject.html")).contains("claudeMdFormat"));
	}

	@Test
	void answersHelpAndChecksNothing() {
		assertDoesNotThrow(() -> run("--help"));

		assertTrue(output().contains("Usage: claude-code-enforcer"), output());
	}

	/** A hook invoking a flag that does not exist must not be quietly given a plain check. */
	@Test
	void refusesAnOptionItDoesNotKnow() {
		IllegalArgumentException thrown = assertThrows(IllegalArgumentException.class,
				() -> run(tempDir.toString(), "--fix-everything"));

		assertTrue(thrown.getMessage().contains("Unknown option: --fix-everything"), thrown.getMessage());
	}

	@Test
	void refusesAnOptionMissingItsValue() {
		assertThrows(IllegalArgumentException.class, () -> run(tempDir.toString(), "--skip"));
	}

	@Test
	void refusesABudgetThatIsNotANumber() {
		IllegalArgumentException thrown = assertThrows(IllegalArgumentException.class,
				() -> run(tempDir.toString(), "--budget", "large"));

		assertTrue(thrown.getMessage().contains("--budget"), thrown.getMessage());
	}

	@Test
	void refusesASecondProjectDirectory() {
		assertThrows(IllegalArgumentException.class, () -> run(tempDir.toString(), tempDir.toString()));
	}

	@Test
	void takesTheBudgetItIsGiven() {
		writeString(tempDir.resolve("CLAUDE.md"), VALID_CLAUDE_MD);

		assertThrows(Main.CheckFailedException.class, () -> run(tempDir.toString(), "--budget", "10"));
		assertDoesNotThrow(() -> run(tempDir.toString(), "--budget", "0"));
	}

	/** Debug says what each rule was pointed at, which is a question asked after a surprise. */
	@Test
	void saysWhatItCheckedOnlyWhenAskedTo() {
		writeString(tempDir.resolve("CLAUDE.md"), VALID_CLAUDE_MD);

		assertDoesNotThrow(() -> run(tempDir.toString()));
		assertFalse(output().contains("[debug]"), output());

		captureTheStreams();
		assertDoesNotThrow(() -> run(tempDir.toString(), "--debug"));
		assertTrue(output().contains("[debug]"), output());
	}

	private void run(String... args) {
		Main.run(args, new PrintStream(out, true, StandardCharsets.UTF_8),
				new PrintStream(err, true, StandardCharsets.UTF_8));
	}

	private String output() {
		return out.toString(StandardCharsets.UTF_8);
	}

	private String errors() {
		return err.toString(StandardCharsets.UTF_8);
	}

	private String readString(Path path) {
		return assertDoesNotThrow(() -> Files.readString(path));
	}
}
