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
	void passesForValidJson() throws IOException {
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
	void failsWhenFileIsEmpty() throws IOException {
		EnforcerRuleException exception = assertThrows(EnforcerRuleException.class, ruleFor("   ")::execute);
		assertTrue(exception.getMessage().contains("empty"), exception.getMessage());
	}

	@Test
	void failsWhenJsonIsMalformed() throws IOException {
		EnforcerRuleException exception = assertThrows(EnforcerRuleException.class,
				ruleFor("{ \"permissions\": ")::execute);
		assertTrue(exception.getMessage().contains("not valid JSON"), exception.getMessage());
	}

	@Test
	void failsWhenARequiredPermissionIsMissing() throws IOException {
		SettingsJsonValidRule rule = ruleFor(VALID_SETTINGS);
		rule.setRequiredPermissions(List.of("Bash(git *)"));

		EnforcerRuleException exception = assertThrows(EnforcerRuleException.class, rule::execute);
		assertTrue(exception.getMessage().contains("missing required permission: Bash(git *)"), exception.getMessage());
	}

	@Test
	void failsWhenAForbiddenPermissionIsPresent() throws IOException {
		SettingsJsonValidRule rule = ruleFor(VALID_SETTINGS);
		rule.setForbiddenPermissions(List.of("Edit"));

		EnforcerRuleException exception = assertThrows(EnforcerRuleException.class, rule::execute);
		assertTrue(exception.getMessage().contains("forbidden permission: Edit"), exception.getMessage());
	}

	@Test
	void passesWhenPermissionPolicyIsSatisfied() throws IOException {
		SettingsJsonValidRule rule = ruleFor(VALID_SETTINGS);
		rule.setRequiredPermissions(List.of("Edit"));
		rule.setForbiddenPermissions(List.of("Bash(*)"));

		assertDoesNotThrow(rule::execute);
	}

	private SettingsJsonValidRule ruleFor(String content) throws IOException {
		Path file = tempDir.resolve("settings.json");
		Files.writeString(file, content);
		SettingsJsonValidRule rule = new SettingsJsonValidRule();
		rule.setSettingsFile(file.toFile());
		return rule;
	}
}
