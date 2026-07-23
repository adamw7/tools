package io.github.adamw7.tools.enforcer.secret;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
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
		EnforcerRuleException exception = assertThrows(EnforcerRuleException.class, new NoSecretsRule()::execute);
		assertTrue(exception.getMessage().contains("files or directories"), exception.getMessage());
	}

	@Test
	void failsWhenThereAreNoPatternsToScanFor() {
		NoSecretsRule rule = ruleForFile("clean");
		rule.setUseDefaultPatterns(false);

		EnforcerRuleException exception = assertThrows(EnforcerRuleException.class, rule::execute);
		assertTrue(exception.getMessage().contains("secretPatterns"), exception.getMessage());
	}

	@Test
	void failsForAnAnthropicKey() {
		EnforcerRuleException exception = assertThrows(EnforcerRuleException.class,
				ruleForFile("{ \"env\": { \"KEY\": \"" + ANTHROPIC_KEY + "\" } }")::execute);
		assertTrue(exception.getMessage().contains("Anthropic API key"), exception.getMessage());
	}

	@Test
	void reportsTheLineWithoutEchoingTheSecret() {
		EnforcerRuleException exception = assertThrows(EnforcerRuleException.class,
				ruleForFile("first line\nkey = " + ANTHROPIC_KEY + "\n")::execute);
		assertTrue(exception.getMessage().contains("line 2"), exception.getMessage());
		assertTrue(exception.getMessage().contains("sk-ant-a..."), exception.getMessage());
		assertFalse(exception.getMessage().contains(ANTHROPIC_KEY), exception.getMessage());
	}

	@Test
	void failsForAGithubToken() {
		EnforcerRuleException exception = assertThrows(EnforcerRuleException.class,
				ruleForFile("token: ghp_ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghij")::execute);
		assertTrue(exception.getMessage().contains("GitHub token"), exception.getMessage());
	}

	@Test
	void failsForAPrivateKeyBlock() {
		EnforcerRuleException exception = assertThrows(EnforcerRuleException.class,
				ruleForFile("-----BEGIN RSA PRIVATE KEY-----")::execute);
		assertTrue(exception.getMessage().contains("private key block"), exception.getMessage());
	}

	@Test
	void scansDirectoriesRecursively() {
		writeString(tempDir.resolve("hooks/nested/script.sh"), "export KEY=" + ANTHROPIC_KEY + "\n");
		NoSecretsRule rule = new NoSecretsRule();
		rule.setDirectories(List.of(tempDir.resolve("hooks").toFile()));

		EnforcerRuleException exception = assertThrows(EnforcerRuleException.class, rule::execute);
		assertTrue(exception.getMessage().contains("script.sh"), exception.getMessage());
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

		EnforcerRuleException exception = assertThrows(EnforcerRuleException.class, rule::execute);
		assertTrue(exception.getMessage().contains("password"), exception.getMessage());
	}

	private NoSecretsRule ruleForFile(String content) {
		Path file = tempDir.resolve("settings.json");
		writeString(file, content);
		NoSecretsRule rule = new NoSecretsRule();
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
