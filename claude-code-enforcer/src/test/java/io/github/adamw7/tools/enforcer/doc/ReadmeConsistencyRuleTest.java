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

import io.github.adamw7.tools.enforcer.rule.CapturingLogger;

class ReadmeConsistencyRuleTest {

	@TempDir
	private Path tempDir;

	@Test
	void passesWhenCapturedValuesAgree() {
		ReadmeConsistencyRule rule = ruleFor("Supports proto2 only.", "This solution supports only proto2.");
		rule.setConsistentPatterns(List.of("proto(\\d)"));

		assertDoesNotThrow(rule::execute);
	}

	@Test
	void failsWhenTheReadmeContradictsTheAgentDocs() {
		ReadmeConsistencyRule rule = ruleFor("Now supports proto3.", "Supports only proto2.");
		rule.setConsistentPatterns(List.of("proto(\\d)"));

		EnforcerRuleException exception = assertThrows(EnforcerRuleException.class, rule::execute);
		assertTrue(exception.getMessage().contains("'3'"), exception.getMessage());
		assertTrue(exception.getMessage().contains("'2'"), exception.getMessage());
	}

	@Test
	void ignoresAFactTheReadmeDoesNotRepeat() {
		ReadmeConsistencyRule rule = ruleFor("No version documented here.", "We target Java 25.");
		rule.setConsistentPatterns(List.of("Java (\\d+)"));

		assertDoesNotThrow(rule::execute);
	}

	@Test
	void ignoresAFactMissingFromTheAgentDocs() {
		ReadmeConsistencyRule rule = ruleFor("We target Java 25.", "No version documented here.");
		rule.setConsistentPatterns(List.of("Java (\\d+)"));

		assertDoesNotThrow(rule::execute);
	}

	@Test
	void ignoresPatternsThatMatchNeitherDocument() {
		ReadmeConsistencyRule rule = ruleFor("Nothing relevant.", "Also nothing.");
		rule.setConsistentPatterns(List.of("Java (\\d+)"));

		assertDoesNotThrow(rule::execute);
	}

	@Test
	void passesWhenNoPatternsAreConfigured() {
		assertDoesNotThrow(ruleFor("a", "b")::execute);
	}

	@Test
	void reportsEveryMismatchTogether() {
		ReadmeConsistencyRule rule = ruleFor("proto3 and Java 24.", "proto2 and Java 25.");
		rule.setConsistentPatterns(List.of("proto(\\d)", "Java (\\d+)"));

		EnforcerRuleException exception = assertThrows(EnforcerRuleException.class, rule::execute);
		assertTrue(exception.getMessage().contains("proto(\\d)"), exception.getMessage());
		assertTrue(exception.getMessage().contains("Java (\\d+)"), exception.getMessage());
	}

	@Test
	void failsWithAClearMessageWhenAPatternHasNoCapturingGroup() {
		ReadmeConsistencyRule rule = ruleFor("proto2.", "proto2.");
		rule.setConsistentPatterns(List.of("proto\\d"));

		EnforcerRuleException exception = assertThrows(EnforcerRuleException.class, rule::execute);
		assertTrue(exception.getMessage().contains("must declare a capturing group"), exception.getMessage());
	}

	@Test
	void downgradesToWarningWhenSeverityIsWarn() {
		ReadmeConsistencyRule rule = ruleFor("Now supports proto3.", "Supports only proto2.");
		rule.setConsistentPatterns(List.of("proto(\\d)"));
		rule.setSeverity("warn");
		CapturingLogger logger = new CapturingLogger();
		rule.setLog(logger);

		assertDoesNotThrow(rule::execute);
		assertTrue(logger.warnings().stream().anyMatch(w -> w.contains("drifted")), logger.warnings().toString());
	}

	@Test
	void failsWhenAFileIsMissing() {
		ReadmeConsistencyRule rule = new ReadmeConsistencyRule();
		rule.setReadmeFile(tempDir.resolve("absent-readme.md").toFile());
		rule.setAgentDocFile(tempDir.resolve("absent-agents.md").toFile());

		EnforcerRuleException exception = assertThrows(EnforcerRuleException.class, rule::execute);
		assertTrue(exception.getMessage().contains("does not exist"), exception.getMessage());
	}

	@Test
	void failsWhenTheReadmeFileIsNotConfigured() {
		ReadmeConsistencyRule rule = new ReadmeConsistencyRule();
		rule.setAgentDocFile(tempDir.resolve("AGENTS.md").toFile());

		EnforcerRuleException exception = assertThrows(EnforcerRuleException.class, rule::execute);
		assertTrue(exception.getMessage().contains("readmeFile"), exception.getMessage());
	}

	private ReadmeConsistencyRule ruleFor(String readmeContent, String agentDocContent) {
		Path readme = tempDir.resolve("README.md");
		Path agents = tempDir.resolve("AGENTS.md");
		writeString(readme, readmeContent);
		writeString(agents, agentDocContent);
		ReadmeConsistencyRule rule = new ReadmeConsistencyRule();
		rule.setReadmeFile(readme.toFile());
		rule.setAgentDocFile(agents.toFile());
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
