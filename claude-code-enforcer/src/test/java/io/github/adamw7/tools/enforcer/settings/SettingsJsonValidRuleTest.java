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

class SettingsJsonValidRuleTest {

	private static final String VALID_SETTINGS = """
			{
			  "permissions": {
			    "allow": [ "Bash(mvn *)", "Edit" ]
			  }
			}
			""";

	@TempDir
	private Path tempDir;

	@Test
	void passesForValidJson() {
		assertDoesNotThrow(ruleFor(VALID_SETTINGS)::execute);
	}

	@Test
	void failsWhenNotConfigured() {
		EnforcerRuleException exception = assertThrows(EnforcerRuleException.class, new SettingsJsonValidRule()::execute);
		assertTrue(exception.getMessage().contains("not configured"), exception.getMessage());
	}

	@Test
	void failsWhenFileIsMissing() {
		SettingsJsonValidRule rule = new SettingsJsonValidRule();
		rule.setSettingsFile(tempDir.resolve("absent.json").toFile());

		EnforcerRuleException exception = assertThrows(EnforcerRuleException.class, rule::execute);
		assertTrue(exception.getMessage().contains("does not exist"), exception.getMessage());
	}

	@Test
	void failsWhenFileIsEmpty() {
		EnforcerRuleException exception = assertThrows(EnforcerRuleException.class, ruleFor("   ")::execute);
		assertTrue(exception.getMessage().contains("empty"), exception.getMessage());
	}

	@Test
	void failsWhenJsonIsMalformed() {
		EnforcerRuleException exception = assertThrows(EnforcerRuleException.class,
				ruleFor("{ \"permissions\": ")::execute);
		assertTrue(exception.getMessage().contains("not valid JSON"), exception.getMessage());
	}

	@Test
	void failsWhenARequiredPermissionIsMissing() {
		SettingsJsonValidRule rule = ruleFor(VALID_SETTINGS);
		rule.setRequiredPermissions(List.of("Bash(git *)"));

		EnforcerRuleException exception = assertThrows(EnforcerRuleException.class, rule::execute);
		assertTrue(exception.getMessage().contains("missing required permission: Bash(git *)"), exception.getMessage());
	}

	@Test
	void failsWhenAForbiddenPermissionIsPresent() {
		SettingsJsonValidRule rule = ruleFor(VALID_SETTINGS);
		rule.setForbiddenPermissions(List.of("Edit"));

		EnforcerRuleException exception = assertThrows(EnforcerRuleException.class, rule::execute);
		assertTrue(exception.getMessage().contains("forbidden permission: Edit"), exception.getMessage());
	}

	@Test
	void passesWhenPermissionPolicyIsSatisfied() {
		SettingsJsonValidRule rule = ruleFor(VALID_SETTINGS);
		rule.setRequiredPermissions(List.of("Edit"));
		rule.setForbiddenPermissions(List.of("Bash(*)"));

		assertDoesNotThrow(rule::execute);
	}

	private SettingsJsonValidRule ruleFor(String content) {
		Path file = tempDir.resolve("settings.json");
		writeString(file, content);
		SettingsJsonValidRule rule = new SettingsJsonValidRule();
		rule.setSettingsFile(file.toFile());
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
