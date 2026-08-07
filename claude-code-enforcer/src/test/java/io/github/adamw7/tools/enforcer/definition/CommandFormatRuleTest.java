package io.github.adamw7.tools.enforcer.definition;

import static io.github.adamw7.tools.enforcer.rule.TestFiles.createDirectory;
import static io.github.adamw7.tools.enforcer.rule.TestFiles.readString;
import static io.github.adamw7.tools.enforcer.rule.TestFiles.writeBytes;
import static io.github.adamw7.tools.enforcer.rule.TestFiles.writeString;
import static io.github.adamw7.tools.test.ExpectedFailures.assertFailure;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.List;

import org.apache.maven.enforcer.rule.api.EnforcerRuleException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import io.github.adamw7.tools.enforcer.rule.CapturingLogger;

class CommandFormatRuleTest {

	@TempDir
	private Path tempDir;

	@Test
	void passesWhenEveryCommandIsWellFormed() {
		writeString(tempDir.resolve("review.md"), """
				---
				description: Reviews the pending changes.
				model: claude-opus-4-8
				---
				Review the diff.
				""");
		writeString(tempDir.resolve("plain.md"), "Just a body, no front matter.");

		assertDoesNotThrow(ruleFor(tempDir)::execute);
	}

	@Test
	void passesWhenNoCommandsExist() {
		assertDoesNotThrow(ruleFor(tempDir)::execute);
	}

	@Test
	void ignoresNonMarkdownFiles() {
		writeString(tempDir.resolve("notes.txt"), "not a command");

		assertDoesNotThrow(ruleFor(tempDir)::execute);
	}

	@Test
	void ignoresSubdirectoriesNamedLikeMarkdown() {
		createDirectory(tempDir.resolve("nested.md"));

		assertDoesNotThrow(ruleFor(tempDir)::execute);
	}

	@Test
	void passesWhenModelIsAllowed() {
		writeString(tempDir.resolve("review.md"), """
				---
				model: claude-opus-4-8
				---
				Review the diff.
				""");
		CommandFormatRule rule = ruleFor(tempDir);
		rule.setAllowedModels(List.of("claude-opus-4-8", "claude-sonnet-4-6"));

		assertDoesNotThrow(rule::execute);
	}

	@Test
	void reportsEveryCommandProblemTogether() {
		writeString(tempDir.resolve("blank.md"), "   ");
		writeString(tempDir.resolve("BadName.md"), "Review the pull request.");

		assertFailure(EnforcerRuleException.class, ruleFor(tempDir)::execute, "blank.md", "BadName.md");
	}

	@Test
	void failsWhenNotConfigured() {
		assertFailure(EnforcerRuleException.class, new CommandFormatRule()::execute, "not configured");
	}

	@Test
	void failsWhenDirectoryIsMissing() {
		assertFailure(EnforcerRuleException.class, ruleFor(tempDir.resolve("absent"))::execute, "does not exist");
	}

	@Test
	void failsWhenCommandIsEmpty() {
		writeString(tempDir.resolve("blank.md"), "   ");

		assertFailure(EnforcerRuleException.class, ruleFor(tempDir)::execute, "is empty");
	}

	@Test
	void failsWhenFileNameIsNotKebabCase() {
		writeString(tempDir.resolve("ReviewPR.md"), "Review the pull request.");

		assertFailure(EnforcerRuleException.class, ruleFor(tempDir)::execute, "must be lower-case kebab-case");
	}

	@Test
	void failsWhenDescriptionIsEmpty() {
		writeString(tempDir.resolve("review.md"), """
				---
				description:
				---
				Review the diff.
				""");

		assertFailure(EnforcerRuleException.class, ruleFor(tempDir)::execute, "description must not be empty");
	}

	@Test
	void failsWhenModelIsNotAllowed() {
		writeString(tempDir.resolve("review.md"), """
				---
				model: claud-opus
				---
				Review the diff.
				""");
		CommandFormatRule rule = ruleFor(tempDir);
		rule.setAllowedModels(List.of("claude-opus-4-8", "claude-sonnet-4-6"));

		assertFailure(EnforcerRuleException.class, rule::execute, "unsupported model 'claud-opus'");
	}

	@Test
	void failsWhenFrontMatterHasUnknownKey() {
		writeString(tempDir.resolve("review.md"), """
				---
				descripton: Reviews the diff.
				---
				Review the diff.
				""");
		CommandFormatRule rule = ruleFor(tempDir);
		rule.setAllowedFrontMatterKeys(List.of("description", "argument-hint", "allowed-tools", "model"));

		assertFailure(EnforcerRuleException.class, rule::execute, "unknown key 'descripton:'");
	}

	@Test
	void warnSeverityLogsInsteadOfFailing() {
		writeString(tempDir.resolve("blank.md"), "   ");
		CommandFormatRule rule = ruleFor(tempDir);
		rule.setSeverity("warn");
		CapturingLogger logger = new CapturingLogger();
		rule.setLog(logger);

		assertDoesNotThrow(rule::execute);
		assertTrue(logger.warnings().stream().anyMatch(w -> w.contains("blank.md")), logger.warnings().toString());
	}

	@Test
	void autoFixRewritesMalformedFrontMatterInPlace() {
		Path file = tempDir.resolve("review.md");
		writeString(file, """
				---
				description: Reviews the diff.
				Review the diff.
				""");
		CommandFormatRule rule = ruleFor(tempDir);
		rule.setAutoFix(true);
		rule.setLog(new CapturingLogger());

		assertDoesNotThrow(rule::execute);
		assertTrue(readString(file).contains("---\nReview the diff."), readString(file));
	}

	@Test
	void reportsACommandThatCannotBeReadAsText() {
		writeBytes(tempDir.resolve("binary.md"), new byte[] { (byte) 0xC3, (byte) 0x28 });

		assertFailure(EnforcerRuleException.class, ruleFor(tempDir)::execute, "cannot be read as text");
	}

	private CommandFormatRule ruleFor(Path commandsDir) {
		CommandFormatRule rule = new CommandFormatRule();
		rule.setCommandsDir(commandsDir.toFile());
		return rule;
	}
}
