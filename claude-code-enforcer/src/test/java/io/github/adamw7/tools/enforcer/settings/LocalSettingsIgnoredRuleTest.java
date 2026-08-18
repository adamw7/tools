package io.github.adamw7.tools.enforcer.settings;

import static io.github.adamw7.tools.test.TestFiles.writeBytes;
import static io.github.adamw7.tools.test.TestFiles.writeString;
import static io.github.adamw7.tools.test.ExpectedFailures.assertFailure;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

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
		assertFailure(EnforcerRuleException.class, new LocalSettingsIgnoredRule()::execute, "not configured");
	}

	@Test
	void failsWhenTheGitignoreIsMissing() {
		LocalSettingsIgnoredRule rule = new LocalSettingsIgnoredRule();
		rule.setGitignoreFile(tempDir.resolve("absent").toFile());

		assertFailure(EnforcerRuleException.class, rule::execute, "does not exist");
	}

	@Test
	void failsWhenThePathIsNotCovered() {
		assertFailure(EnforcerRuleException.class, ruleFor("target/\n*.log\n")::execute,
				"does not cover: .claude/settings.local.json");
	}

	@Test
	void failsWhenANegationReincludesThePath() {
		assertFailure(EnforcerRuleException.class, ruleFor("*.local.json\n!settings.local.json\n")::execute,
				"does not cover");
	}

	@Test
	void checksEveryConfiguredPath() {
		LocalSettingsIgnoredRule rule = ruleFor(".claude/settings.local.json\n");
		rule.setIgnoredPaths(List.of("./.claude/settings.local.json", ".env"));

		assertFailure(EnforcerRuleException.class, rule::execute, "does not cover: .env");
	}

	@Test
	void failsWithAVerdictWhenTheGitignoreIsNotUtf8Text() {
		// Reading it as text threw an UncheckedIOException straight out of the rule,
		// which aborts the build as an internal error rather than as the verdict the
		// rule exists to give.
		Path gitignore = writeBytes(tempDir.resolve(".gitignore"), new byte[] { 'a', (byte) 0xE9, '\n' });
		LocalSettingsIgnoredRule rule = new LocalSettingsIgnoredRule();
		rule.setGitignoreFile(gitignore.toFile());

		assertFailure(EnforcerRuleException.class, rule::execute, "cannot be read as UTF-8 text");
	}

	private LocalSettingsIgnoredRule ruleFor(String gitignoreContent) {
		Path file = tempDir.resolve(".gitignore");
		writeString(file, gitignoreContent);
		LocalSettingsIgnoredRule rule = new LocalSettingsIgnoredRule();
		rule.setGitignoreFile(file.toFile());
		return rule;
	}

}
