package io.github.adamw7.tools.enforcer.secret;

import static io.github.adamw7.tools.test.TestFiles.writeString;
import static io.github.adamw7.tools.test.ExpectedFailures.assertFailure;
import static io.github.adamw7.tools.test.TestStrings.occurrences;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.nio.file.Path;
import java.util.List;

import org.apache.maven.enforcer.rule.api.EnforcerRuleException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class NoSecretsRuleTest {

	private static final String ANTHROPIC_KEY = "sk-ant-api03-abcdefghijklmnop";

	@TempDir
	private Path tempDir;

	@Test
	void passesForACleanFile() {
		assertDoesNotThrow(ruleForFile("{ \"env\": { \"API_KEY\": \"${API_KEY}\" } }")::execute);
	}

	@Test
	void passesWhenTheFileIsAbsent() {
		NoSecretsRule rule = new NoSecretsRule();
		rule.setFiles(List.of(tempDir.resolve("absent.json").toFile()));

		assertDoesNotThrow(rule::execute);
	}

	@Test
	void failsWhenNoTargetsAreConfigured() {
		assertFailure(EnforcerRuleException.class, new NoSecretsRule()::execute, "files or directories");
	}

	@Test
	void failsWhenThereAreNoPatternsToScanFor() {
		NoSecretsRule rule = ruleForFile("clean");
		rule.setUseDefaultPatterns(false);

		assertFailure(EnforcerRuleException.class, rule::execute, "secretPatterns");
	}

	@Test
	void failsForAnAnthropicKey() {
		assertFailure(EnforcerRuleException.class,
				ruleForFile("{ \"env\": { \"KEY\": \"" + ANTHROPIC_KEY + "\" } }")::execute, "Anthropic API key");
	}

	@Test
	void reportsTheLineWithoutEchoingTheSecret() {
		EnforcerRuleException exception = assertFailure(EnforcerRuleException.class,
				ruleForFile("first line\nkey = " + ANTHROPIC_KEY + "\n")::execute, "line 2", "sk-ant-a...");
		assertFalse(exception.getMessage().contains(ANTHROPIC_KEY), exception.getMessage());
	}

	@Test
	void failsForAGithubToken() {
		assertFailure(EnforcerRuleException.class,
				ruleForFile("token: ghp_ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghij")::execute, "GitHub token");
	}

	@Test
	void failsForAPrivateKeyBlock() {
		assertFailure(EnforcerRuleException.class, ruleForFile("-----BEGIN RSA PRIVATE KEY-----")::execute,
				"private key block");
	}

	@Test
	void scansDirectoriesRecursively() {
		writeString(tempDir.resolve("hooks/nested/script.sh"), "export KEY=" + ANTHROPIC_KEY + "\n");
		NoSecretsRule rule = new NoSecretsRule();
		rule.setDirectories(List.of(tempDir.resolve("hooks").toFile()));

		assertFailure(EnforcerRuleException.class, rule::execute, "script.sh");
	}

	@Test
	void skipsAnAbsentDirectory() {
		NoSecretsRule rule = new NoSecretsRule();
		rule.setDirectories(List.of(tempDir.resolve("absent").toFile()));

		assertDoesNotThrow(rule::execute);
	}

	@Test
	void scansCustomPatterns() {
		NoSecretsRule rule = ruleForFile("password = hunter2");
		rule.setSecretPatterns(List.of("password\\s*=\\s*\\S+"));

		assertFailure(EnforcerRuleException.class, rule::execute, "password");
	}

	@Test
	void failsWithAClearMessageForAPatternThatIsNotAValidRegularExpression() {
		NoSecretsRule rule = ruleForFile("nothing to see here");
		rule.setSecretPatterns(List.of("(unclosed"));

		assertFailure(EnforcerRuleException.class, rule::execute, "not a valid regular expression", "(unclosed");
	}

	@Test
	void reportsAFileNamedAndScannedAsADirectoryEntryOnlyOnce() {
		Path file = writeString(tempDir.resolve("hooks/script.sh"), "export KEY=" + ANTHROPIC_KEY + "\n");
		NoSecretsRule rule = new NoSecretsRule();
		rule.setFiles(List.of(file.toFile()));
		rule.setDirectories(List.of(tempDir.resolve("hooks").toFile()));

		EnforcerRuleException exception = assertThrows(EnforcerRuleException.class, rule::execute);
		assertEquals(1, occurrences(exception.getMessage(), "script.sh"), exception.getMessage());
	}

	@Test
	void failsWhenAScannedDirectoryIsAFile() {
		// A <directory> pointed at a file scanned nothing and reported nothing, which
		// reads exactly like a clean scan.
		Path file = writeString(tempDir.resolve("hooks"), "#!/bin/sh\n");
		NoSecretsRule rule = new NoSecretsRule();
		rule.setDirectories(List.of(file.toFile()));

		assertFailure(EnforcerRuleException.class, rule::execute, "is not a directory");
	}

	private NoSecretsRule ruleForFile(String content) {
		Path file = tempDir.resolve("settings.json");
		writeString(file, content);
		NoSecretsRule rule = new NoSecretsRule();
		rule.setFiles(List.of(file.toFile()));
		return rule;
	}

}
