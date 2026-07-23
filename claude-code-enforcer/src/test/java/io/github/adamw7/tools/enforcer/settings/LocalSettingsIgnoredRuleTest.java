package io.github.adamw7.tools.enforcer.settings;

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

class LocalSettingsIgnoredRuleTest {

	@TempDir
	private Path tempDir;

	@Test
	void passesForAVerbatimEntry() {
		assertDoesNotThrow(ruleFor(".claude/settings.local.json\n")::execute);
	}

	@Test
	void passesForABasenameGlob() {
		assertDoesNotThrow(ruleFor("*.local.json\n")::execute);
	}

	@Test
	void passesForAnIgnoredAncestorDirectory() {
		assertDoesNotThrow(ruleFor(".claude/\n")::execute);
	}

	@Test
	void failsWhenNotConfigured() {
		EnforcerRuleException exception = assertThrows(EnforcerRuleException.class,
				new LocalSettingsIgnoredRule()::execute);
		assertTrue(exception.getMessage().contains("not configured"), exception.getMessage());
	}

	@Test
	void failsWhenTheGitignoreIsMissing() {
		LocalSettingsIgnoredRule rule = new LocalSettingsIgnoredRule();
		rule.setGitignoreFile(tempDir.resolve("absent").toFile());

		EnforcerRuleException exception = assertThrows(EnforcerRuleException.class, rule::execute);
		assertTrue(exception.getMessage().contains("does not exist"), exception.getMessage());
	}

	@Test
	void failsWhenThePathIsNotCovered() {
		EnforcerRuleException exception = assertThrows(EnforcerRuleException.class,
				ruleFor("target/\n*.log\n")::execute);
		assertTrue(exception.getMessage().contains("does not cover: .claude/settings.local.json"),
				exception.getMessage());
	}

	@Test
	void failsWhenANegationReincludesThePath() {
		EnforcerRuleException exception = assertThrows(EnforcerRuleException.class,
				ruleFor("*.local.json\n!settings.local.json\n")::execute);
		assertTrue(exception.getMessage().contains("does not cover"), exception.getMessage());
	}

	@Test
	void checksEveryConfiguredPath() {
		LocalSettingsIgnoredRule rule = ruleFor(".claude/settings.local.json\n");
		rule.setIgnoredPaths(List.of("./.claude/settings.local.json", ".env"));

		EnforcerRuleException exception = assertThrows(EnforcerRuleException.class, rule::execute);
		assertTrue(exception.getMessage().contains("does not cover: .env"), exception.getMessage());
	}

	private LocalSettingsIgnoredRule ruleFor(String gitignoreContent) {
		Path file = tempDir.resolve(".gitignore");
		writeString(file, gitignoreContent);
		LocalSettingsIgnoredRule rule = new LocalSettingsIgnoredRule();
		rule.setGitignoreFile(file.toFile());
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
