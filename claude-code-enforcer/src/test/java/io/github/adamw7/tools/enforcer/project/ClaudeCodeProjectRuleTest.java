package io.github.adamw7.tools.enforcer.project;

import static io.github.adamw7.tools.test.TestFiles.createDirectory;
import static io.github.adamw7.tools.test.TestFiles.writeString;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.List;

import org.apache.maven.enforcer.rule.api.EnforcerRuleException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import io.github.adamw7.tools.enforcer.rule.CapturingLogger;

/**
 * The composite as a project meets it: configured with a directory and nothing
 * else, checking what the project has and staying quiet about what it does not.
 */
class ClaudeCodeProjectRuleTest {

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

	@Test
	void passesForAProjectWhoseConfigurationIsWellFormed() {
		writeString(tempDir.resolve("CLAUDE.md"), VALID_CLAUDE_MD);
		writeString(tempDir.resolve(".gitignore"), ".claude/settings.local.json\n");
		createDirectory(tempDir.resolve(".claude"));
		writeString(tempDir.resolve(".claude/settings.json"), "{}");

		assertDoesNotThrow(ruleFor(tempDir)::execute);
	}

	/** One directory, not sixty paths: the whole point of the rule. */
	@Test
	void findsAViolationInAFileNobodyPointedItAt() {
		writeString(tempDir.resolve("CLAUDE.md"), VALID_CLAUDE_MD);
		createDirectory(tempDir.resolve(".claude"));
		writeString(tempDir.resolve(".claude/settings.json"), "{ not json");

		EnforcerRuleException thrown = assertThrows(EnforcerRuleException.class, ruleFor(tempDir)::execute);

		assertTrue(thrown.getMessage().contains("settings.json"), thrown.getMessage());
	}

	/** A violation has to say which part found it, since that is the name to look up. */
	@Test
	void namesThePartThatFoundEachViolation() {
		writeString(tempDir.resolve("CLAUDE.md"), "# CLAUDE.md\n\nNothing else.\n");

		EnforcerRuleException thrown = assertThrows(EnforcerRuleException.class, ruleFor(tempDir)::execute);

		assertTrue(thrown.getMessage().contains("[claudeMdFormat]"), thrown.getMessage());
	}

	/** Every part reports into one message, so one run says everything that is wrong. */
	@Test
	void reportsEveryPartsViolationsTogether() {
		writeString(tempDir.resolve("CLAUDE.md"), "# CLAUDE.md\n\nNothing else.\n");
		createDirectory(tempDir.resolve(".claude"));
		writeString(tempDir.resolve(".claude/settings.json"), "{ not json");

		EnforcerRuleException thrown = assertThrows(EnforcerRuleException.class, ruleFor(tempDir)::execute);

		assertTrue(thrown.getMessage().contains("[claudeMdFormat]"), thrown.getMessage());
		assertTrue(thrown.getMessage().contains("[settingsJsonValid]"), thrown.getMessage());
	}

	/**
	 * The difference from wiring the rules by hand: nothing named these paths, so an
	 * absent one is a project without that thing rather than a mistyped parameter.
	 */
	@Test
	void skipsAPartWhoseInputTheProjectDoesNotHave() {
		writeString(tempDir.resolve("CLAUDE.md"), VALID_CLAUDE_MD);

		assertDoesNotThrow(ruleFor(tempDir)::execute);
	}

	/** A part starts checking the day the project grows the thing it checks. */
	@Test
	void checksADirectoryTheProjectAddsLater() {
		writeString(tempDir.resolve("CLAUDE.md"), VALID_CLAUDE_MD);
		assertDoesNotThrow(ruleFor(tempDir)::execute);

		createDirectory(tempDir.resolve(".claude"));
		createDirectory(tempDir.resolve(".claude/skills"));
		createDirectory(tempDir.resolve(".claude/skills/broken"));

		EnforcerRuleException thrown = assertThrows(EnforcerRuleException.class, ruleFor(tempDir)::execute);
		assertTrue(thrown.getMessage().contains("[skillFilesExist]"), thrown.getMessage());
	}

	@Test
	void skipsThePartsTheProjectNames() {
		writeString(tempDir.resolve("CLAUDE.md"), VALID_CLAUDE_MD);
		createDirectory(tempDir.resolve(".claude"));
		createDirectory(tempDir.resolve(".claude/skills"));
		createDirectory(tempDir.resolve(".claude/skills/broken"));
		ClaudeCodeProjectRule rule = ruleFor(tempDir);
		rule.setSkippedRules(List.of("skillFilesExist", "uniqueNames", "uniqueDescriptions"));

		assertDoesNotThrow(rule::execute);
	}

	@Test
	void skippingIsNotASpellingTest() {
		writeString(tempDir.resolve("CLAUDE.md"), "# CLAUDE.md\n\nNothing else.\n");
		ClaudeCodeProjectRule rule = ruleFor(tempDir);
		rule.setSkippedRules(List.of("  ClaudeMdFormat  ", "memoryImports", "contextBudget"));

		assertDoesNotThrow(rule::execute);
	}

	/** A project directory was configured, so one that is not there is a setup mistake. */
	@Test
	void failsWhenTheProjectDirectoryIsNotThere() {
		ClaudeCodeProjectRule rule = ruleFor(tempDir.resolve("absent"));

		EnforcerRuleException thrown = assertThrows(EnforcerRuleException.class, rule::execute);

		assertTrue(thrown.getMessage().contains("projectDir does not exist"), thrown.getMessage());
	}

	@Test
	void failsWhenTheProjectDirectoryIsNotConfigured() {
		EnforcerRuleException thrown = assertThrows(EnforcerRuleException.class,
				new ClaudeCodeProjectRule()::execute);

		assertTrue(thrown.getMessage().contains("projectDir"), thrown.getMessage());
	}

	/**
	 * A project with nothing to check passes exactly like one whose configuration is
	 * spotless, so the run has to say which of the two it was.
	 */
	@Test
	void warnsWhenItFoundNothingToCheckAtAll() {
		ClaudeCodeProjectRule rule = ruleFor(tempDir);
		CapturingLogger logger = new CapturingLogger();
		rule.setLog(logger);

		assertDoesNotThrow(rule::execute);
		assertTrue(logger.warnings().stream().anyMatch(warning -> warning.contains("no Claude Code configuration")),
				logger.warnings().toString());
	}

	@Test
	void debugLogsWhichPartsItRan() {
		writeString(tempDir.resolve("CLAUDE.md"), VALID_CLAUDE_MD);
		ClaudeCodeProjectRule rule = ruleFor(tempDir);
		CapturingLogger logger = new CapturingLogger();
		rule.setLog(logger);

		assertDoesNotThrow(rule::execute);
		assertTrue(logger.debugs().stream().anyMatch(debug -> debug.contains("claudeMdFormat")),
				logger.debugs().toString());
	}

	/** One severity covers the whole configuration, which is why it is worth having. */
	@Test
	void oneSeverityCoversEveryPart() {
		writeString(tempDir.resolve("CLAUDE.md"), "# CLAUDE.md\n\nNothing else.\n");
		ClaudeCodeProjectRule rule = ruleFor(tempDir);
		rule.setSeverity("warn");
		CapturingLogger logger = new CapturingLogger();
		rule.setLog(logger);

		assertDoesNotThrow(rule::execute);
		assertFalse(logger.warnings().isEmpty());
	}

	/** The one part of the convention that is a number: a CLAUDE.md is a summary. */
	@Test
	void holdsClaudeMdToTheContextBudgetByConvention() {
		writeString(tempDir.resolve("CLAUDE.md"), VALID_CLAUDE_MD + "x".repeat(40000));

		EnforcerRuleException thrown = assertThrows(EnforcerRuleException.class, ruleFor(tempDir)::execute);

		assertTrue(thrown.getMessage().contains("[contextBudget]"), thrown.getMessage());
	}

	@Test
	void takesTheContextBudgetTheProjectSets() {
		writeString(tempDir.resolve("CLAUDE.md"), VALID_CLAUDE_MD + "x".repeat(40000));
		ClaudeCodeProjectRule rule = ruleFor(tempDir);
		rule.setClaudeMdBudgetBytes(100000);

		assertDoesNotThrow(rule::execute);
	}

	@Test
	void checksNoContextBudgetWhenSetToZero() {
		writeString(tempDir.resolve("CLAUDE.md"), VALID_CLAUDE_MD + "x".repeat(40000));
		ClaudeCodeProjectRule rule = ruleFor(tempDir);
		rule.setClaudeMdBudgetBytes(0);

		assertDoesNotThrow(rule::execute);
	}

	@Test
	void autoFixReachesTheDocumentParts() {
		writeString(tempDir.resolve("CLAUDE.md"), "## Project purpose\nA project.\n");
		ClaudeCodeProjectRule rule = ruleFor(tempDir);
		rule.setAutoFix(true);

		assertDoesNotThrow(rule::execute);
	}

	private ClaudeCodeProjectRule ruleFor(Path projectDir) {
		ClaudeCodeProjectRule rule = new ClaudeCodeProjectRule();
		rule.setProjectDir(projectDir.toFile());
		return rule;
	}
}
