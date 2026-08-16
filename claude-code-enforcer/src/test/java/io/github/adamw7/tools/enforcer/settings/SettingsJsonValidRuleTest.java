package io.github.adamw7.tools.enforcer.settings;

import static io.github.adamw7.tools.test.TestFiles.writeString;
import static io.github.adamw7.tools.test.ExpectedFailures.assertFailure;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

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
		assertFailure(EnforcerRuleException.class, new SettingsJsonValidRule()::execute, "not configured");
	}

	@Test
	void failsWhenFileIsMissing() {
		SettingsJsonValidRule rule = new SettingsJsonValidRule();
		rule.setSettingsFile(tempDir.resolve("absent.json").toFile());

		assertFailure(EnforcerRuleException.class, rule::execute, "does not exist");
	}

	@Test
	void failsWhenFileIsEmpty() {
		assertFailure(EnforcerRuleException.class, ruleFor("   ")::execute, "empty");
	}

	@Test
	void failsWhenJsonIsMalformed() {
		assertFailure(EnforcerRuleException.class, ruleFor("{ \"permissions\": ")::execute, "not valid JSON");
	}

	@Test
	void failsWhenARequiredPermissionIsMissing() {
		SettingsJsonValidRule rule = ruleFor(VALID_SETTINGS);
		rule.setRequiredPermissions(List.of("Bash(git *)"));

		assertFailure(EnforcerRuleException.class, rule::execute, "missing required permission: Bash(git *)");
	}

	@Test
	void failsWhenAForbiddenPermissionIsPresent() {
		SettingsJsonValidRule rule = ruleFor(VALID_SETTINGS);
		rule.setForbiddenPermissions(List.of("Edit"));

		assertFailure(EnforcerRuleException.class, rule::execute, "forbidden permission: Edit");
	}

	@Test
	void passesWhenPermissionPolicyIsSatisfied() {
		SettingsJsonValidRule rule = ruleFor(VALID_SETTINGS);
		rule.setRequiredPermissions(List.of("Edit"));
		rule.setForbiddenPermissions(List.of("Bash(*)"));

		assertDoesNotThrow(rule::execute);
	}

	/**
	 * A permission is a string, and a list element of any other type grants nothing —
	 * so it must not answer for a required permission either. Reading it as its text
	 * let a JSON number satisfy a policy no settings file can actually satisfy, and
	 * it is what {@code permissionsFormat} reports as not being a string at all.
	 */
	@Test
	void doesNotLetANonStringEntrySatisfyARequiredPermission() {
		SettingsJsonValidRule rule = ruleFor("""
				{
				  "permissions": {
				    "allow": [ 123 ]
				  }
				}
				""");
		rule.setRequiredPermissions(List.of("123"));

		assertFailure(EnforcerRuleException.class, rule::execute, "missing required permission: 123");
	}

	private SettingsJsonValidRule ruleFor(String content) {
		Path file = tempDir.resolve("settings.json");
		writeString(file, content);
		SettingsJsonValidRule rule = new SettingsJsonValidRule();
		rule.setSettingsFile(file.toFile());
		return rule;
	}

}
