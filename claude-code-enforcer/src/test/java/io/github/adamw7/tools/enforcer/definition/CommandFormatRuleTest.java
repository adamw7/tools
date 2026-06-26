package io.github.adamw7.tools.enforcer.definition;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
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

		EnforcerRuleException exception = assertThrows(EnforcerRuleException.class, ruleFor(tempDir)::execute);
		assertTrue(exception.getMessage().contains("blank.md"), exception.getMessage());
		assertTrue(exception.getMessage().contains("BadName.md"), exception.getMessage());
	}

	@Test
	void failsWhenNotConfigured() {
		EnforcerRuleException exception = assertThrows(EnforcerRuleException.class, new CommandFormatRule()::execute);
		assertTrue(exception.getMessage().contains("not configured"), exception.getMessage());
	}

	@Test
	void failsWhenDirectoryIsMissing() {
		EnforcerRuleException exception = assertThrows(EnforcerRuleException.class,
				ruleFor(tempDir.resolve("absent"))::execute);
		assertTrue(exception.getMessage().contains("does not exist"), exception.getMessage());
	}

	@Test
	void failsWhenCommandIsEmpty() {
		writeString(tempDir.resolve("blank.md"), "   ");

		EnforcerRuleException exception = assertThrows(EnforcerRuleException.class, ruleFor(tempDir)::execute);
		assertTrue(exception.getMessage().contains("is empty"), exception.getMessage());
	}

	@Test
	void failsWhenFileNameIsNotKebabCase() {
		writeString(tempDir.resolve("ReviewPR.md"), "Review the pull request.");

		EnforcerRuleException exception = assertThrows(EnforcerRuleException.class, ruleFor(tempDir)::execute);
		assertTrue(exception.getMessage().contains("must be lower-case kebab-case"), exception.getMessage());
	}

	@Test
	void failsWhenDescriptionIsEmpty() {
		writeString(tempDir.resolve("review.md"), """
				---
				description:
				---
				Review the diff.
				""");

		EnforcerRuleException exception = assertThrows(EnforcerRuleException.class, ruleFor(tempDir)::execute);
		assertTrue(exception.getMessage().contains("description must not be empty"), exception.getMessage());
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

		EnforcerRuleException exception = assertThrows(EnforcerRuleException.class, rule::execute);
		assertTrue(exception.getMessage().contains("unsupported model 'claud-opus'"), exception.getMessage());
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

		EnforcerRuleException exception = assertThrows(EnforcerRuleException.class, rule::execute);
		assertTrue(exception.getMessage().contains("unknown key 'descripton:'"), exception.getMessage());
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

	private CommandFormatRule ruleFor(Path commandsDir) {
		CommandFormatRule rule = new CommandFormatRule();
		rule.setCommandsDir(commandsDir.toFile());
		return rule;
	}

	private static void writeString(Path file, String content) {
		try {
			Files.writeString(file, content);
		} catch (IOException e) {
			throw new UncheckedIOException("Could not write " + file, e);
		}
	}

	private static void createDirectory(Path dir) {
		try {
			Files.createDirectory(dir);
		} catch (IOException e) {
			throw new UncheckedIOException("Could not create " + dir, e);
		}
	}
}
