package io.github.adamw7.tools.enforcer;

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

class CrossDocConsistencyRuleTest {

	@TempDir
	private Path tempDir;

	@Test
	void passesWhenCapturedValuesAgree() {
		CrossDocConsistencyRule rule = ruleFor("Java 25 is required.", "We target Java 25.");
		rule.setConsistentPatterns(List.of("Java (\\d+)"));

		assertDoesNotThrow(rule::execute);
	}

	@Test
	void failsWhenCapturedValuesDiffer() {
		CrossDocConsistencyRule rule = ruleFor("Java 25 is required.", "We target Java 24.");
		rule.setConsistentPatterns(List.of("Java (\\d+)"));

		EnforcerRuleException exception = assertThrows(EnforcerRuleException.class, rule::execute);
		assertTrue(exception.getMessage().contains("'25'"), exception.getMessage());
		assertTrue(exception.getMessage().contains("'24'"), exception.getMessage());
	}

	@Test
	void failsWhenAFactAppearsInOnlyOneDocument() {
		CrossDocConsistencyRule rule = ruleFor("Java 25 is required.", "No version here.");
		rule.setConsistentPatterns(List.of("Java (\\d+)"));

		EnforcerRuleException exception = assertThrows(EnforcerRuleException.class, rule::execute);
		assertTrue(exception.getMessage().contains("<absent>"), exception.getMessage());
	}

	@Test
	void ignoresPatternsThatMatchNeitherDocument() {
		CrossDocConsistencyRule rule = ruleFor("Nothing relevant.", "Also nothing.");
		rule.setConsistentPatterns(List.of("Java (\\d+)"));

		assertDoesNotThrow(rule::execute);
	}

	@Test
	void passesWhenNoPatternsAreConfigured() {
		assertDoesNotThrow(ruleFor("a", "b")::execute);
	}

	@Test
	void failsWithAClearMessageWhenAPatternHasNoCapturingGroup() {
		CrossDocConsistencyRule rule = ruleFor("Java 25.", "Java 25.");
		rule.setConsistentPatterns(List.of("Java \\d+"));

		EnforcerRuleException exception = assertThrows(EnforcerRuleException.class, rule::execute);
		assertTrue(exception.getMessage().contains("must declare a capturing group"), exception.getMessage());
	}

	@Test
	void failsWhenAFileIsMissing() {
		CrossDocConsistencyRule rule = new CrossDocConsistencyRule();
		rule.setClaudeMdFile(tempDir.resolve("absent-claude.md").toFile());
		rule.setAgentsMdFile(tempDir.resolve("absent-agents.md").toFile());

		EnforcerRuleException exception = assertThrows(EnforcerRuleException.class, rule::execute);
		assertTrue(exception.getMessage().contains("does not exist"), exception.getMessage());
	}

	private CrossDocConsistencyRule ruleFor(String claudeContent, String agentsContent) {
		Path claude = tempDir.resolve("CLAUDE.md");
		Path agents = tempDir.resolve("AGENTS.md");
		writeString(claude, claudeContent);
		writeString(agents, agentsContent);
		CrossDocConsistencyRule rule = new CrossDocConsistencyRule();
		rule.setClaudeMdFile(claude.toFile());
		rule.setAgentsMdFile(agents.toFile());
		return rule;
	}

	private static void writeString(Path file, String content) {
		try {
			Files.writeString(file, content);
		} catch (IOException e) {
			throw new UncheckedIOException("Could not write " + file, e);
		}
	}
}
