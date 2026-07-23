package io.github.adamw7.tools.enforcer.doc;

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

class ContextBudgetRuleTest {

	@TempDir
	private Path tempDir;

	@Test
	void passesWhenTheFileFitsTheBudget() {
		ContextBudgetRule rule = ruleForFile("# CLAUDE.md\n\nShort.\n");
		rule.setMaxBytes(1000);

		assertDoesNotThrow(rule::execute);
	}

	@Test
	void failsWhenNoLimitIsConfigured() {
		EnforcerRuleException exception = assertThrows(EnforcerRuleException.class,
				ruleForFile("content")::execute);
		assertTrue(exception.getMessage().contains("maxBytes, maxLines, or maxTokens"), exception.getMessage());
	}

	@Test
	void failsWhenNoTargetsAreConfigured() {
		ContextBudgetRule rule = new ContextBudgetRule();
		rule.setMaxBytes(1000);

		EnforcerRuleException exception = assertThrows(EnforcerRuleException.class, rule::execute);
		assertTrue(exception.getMessage().contains("files or directories"), exception.getMessage());
	}

	@Test
	void failsWhenAConfiguredFileIsMissing() {
		ContextBudgetRule rule = new ContextBudgetRule();
		rule.setMaxBytes(1000);
		rule.setFiles(List.of(tempDir.resolve("absent.md").toFile()));

		EnforcerRuleException exception = assertThrows(EnforcerRuleException.class, rule::execute);
		assertTrue(exception.getMessage().contains("does not exist"), exception.getMessage());
	}

	@Test
	void failsWhenTheByteBudgetIsExceeded() {
		ContextBudgetRule rule = ruleForFile("x".repeat(100));
		rule.setMaxBytes(50);

		EnforcerRuleException exception = assertThrows(EnforcerRuleException.class, rule::execute);
		assertTrue(exception.getMessage().contains("over the 50-byte budget"), exception.getMessage());
	}

	@Test
	void failsWhenTheLineBudgetIsExceeded() {
		ContextBudgetRule rule = ruleForFile("one\ntwo\nthree\n");
		rule.setMaxLines(2);

		EnforcerRuleException exception = assertThrows(EnforcerRuleException.class, rule::execute);
		assertTrue(exception.getMessage().contains("over the 2-line budget"), exception.getMessage());
	}

	@Test
	void failsWhenTheTokenBudgetIsExceeded() {
		ContextBudgetRule rule = ruleForFile("x".repeat(100));
		rule.setMaxTokens(10);

		EnforcerRuleException exception = assertThrows(EnforcerRuleException.class, rule::execute);
		assertTrue(exception.getMessage().contains("estimated 25 tokens, over the 10-token budget"),
				exception.getMessage());
	}

	@Test
	void measuresMarkdownFilesUnderDirectories() {
		writeString(tempDir.resolve("skills/big/SKILL.md"), "x".repeat(100));
		ContextBudgetRule rule = new ContextBudgetRule();
		rule.setDirectories(List.of(tempDir.resolve("skills").toFile()));
		rule.setMaxBytes(50);

		EnforcerRuleException exception = assertThrows(EnforcerRuleException.class, rule::execute);
		assertTrue(exception.getMessage().contains("SKILL.md"), exception.getMessage());
	}

	@Test
	void skipsNonMarkdownFilesAndAbsentDirectories() {
		writeString(tempDir.resolve("skills/big/data.json"), "x".repeat(100));
		ContextBudgetRule rule = new ContextBudgetRule();
		rule.setDirectories(List.of(tempDir.resolve("skills").toFile(), tempDir.resolve("absent").toFile()));
		rule.setMaxBytes(50);

		assertDoesNotThrow(rule::execute);
	}

	private ContextBudgetRule ruleForFile(String content) {
		Path file = tempDir.resolve("CLAUDE.md");
		writeString(file, content);
		ContextBudgetRule rule = new ContextBudgetRule();
		rule.setFiles(List.of(file.toFile()));
		return rule;
	}

	private static void writeString(Path file, String content) {
		try {
			Files.createDirectories(file.getParent());
			Files.writeString(file, content);
		} catch (IOException e) {
			throw new UncheckedIOException("Could not write " + file, e);
		}
	}
}
