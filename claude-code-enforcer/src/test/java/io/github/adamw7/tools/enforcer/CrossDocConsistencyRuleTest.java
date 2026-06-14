package io.github.adamw7.tools.enforcer;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
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
	void passesWhenCapturedValuesAgree() throws IOException {
		CrossDocConsistencyRule rule = ruleFor("Java 25 is required.", "We target Java 25.");
		rule.setConsistentPatterns(List.of("Java (\\d+)"));

		assertDoesNotThrow(rule::execute);
	}

	@Test
	void failsWhenCapturedValuesDiffer() throws IOException {
		CrossDocConsistencyRule rule = ruleFor("Java 25 is required.", "We target Java 24.");
		rule.setConsistentPatterns(List.of("Java (\\d+)"));

		EnforcerRuleException exception = assertThrows(EnforcerRuleException.class, rule::execute);
		assertTrue(exception.getMessage().contains("'25'"), exception.getMessage());
		assertTrue(exception.getMessage().contains("'24'"), exception.getMessage());
	}

	@Test
	void failsWhenAFactAppearsInOnlyOneDocument() throws IOException {
		CrossDocConsistencyRule rule = ruleFor("Java 25 is required.", "No version here.");
		rule.setConsistentPatterns(List.of("Java (\\d+)"));

		EnforcerRuleException exception = assertThrows(EnforcerRuleException.class, rule::execute);
		assertTrue(exception.getMessage().contains("<absent>"), exception.getMessage());
	}

	@Test
	void ignoresPatternsThatMatchNeitherDocument() throws IOException {
		CrossDocConsistencyRule rule = ruleFor("Nothing relevant.", "Also nothing.");
		rule.setConsistentPatterns(List.of("Java (\\d+)"));

		assertDoesNotThrow(rule::execute);
	}

	@Test
	void passesWhenNoPatternsAreConfigured() throws IOException {
		assertDoesNotThrow(ruleFor("a", "b")::execute);
	}

	@Test
	void failsWhenAFileIsMissing() {
		CrossDocConsistencyRule rule = new CrossDocConsistencyRule();
		rule.setClaudeMdFile(tempDir.resolve("absent-claude.md").toFile());
		rule.setAgentsMdFile(tempDir.resolve("absent-agents.md").toFile());

		EnforcerRuleException exception = assertThrows(EnforcerRuleException.class, rule::execute);
		assertTrue(exception.getMessage().contains("does not exist"), exception.getMessage());
	}

	private CrossDocConsistencyRule ruleFor(String claudeContent, String agentsContent) throws IOException {
		Path claude = tempDir.resolve("CLAUDE.md");
		Path agents = tempDir.resolve("AGENTS.md");
		Files.writeString(claude, claudeContent);
		Files.writeString(agents, agentsContent);
		CrossDocConsistencyRule rule = new CrossDocConsistencyRule();
		rule.setClaudeMdFile(claude.toFile());
		rule.setAgentsMdFile(agents.toFile());
		return rule;
	}
}
