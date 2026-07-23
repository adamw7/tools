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

class PermissionsFormatRuleTest {

	private static final String VALID_SETTINGS = """
			{
			  "permissions": {
			    "allow": [ "Bash(mvn *)", "Edit" ],
			    "deny": [ "WebFetch" ],
			    "ask": [ "Write" ]
			  }
			}
			""";

	@TempDir
	private Path tempDir;

	@Test
	void passesForWellFormedPermissions() {
		assertDoesNotThrow(ruleFor(VALID_SETTINGS)::execute);
	}

	@Test
	void passesWhenPermissionsSectionIsAbsent() {
		assertDoesNotThrow(ruleFor("{ \"env\": {} }")::execute);
	}

	@Test
	void failsWhenNotConfigured() {
		EnforcerRuleException exception = assertThrows(EnforcerRuleException.class,
				new PermissionsFormatRule()::execute);
		assertTrue(exception.getMessage().contains("not configured"), exception.getMessage());
	}

	@Test
	void failsWhenPermissionsIsNotAnObject() {
		EnforcerRuleException exception = assertThrows(EnforcerRuleException.class,
				ruleFor("{ \"permissions\": [] }")::execute);
		assertTrue(exception.getMessage().contains("must be a JSON object"), exception.getMessage());
	}

	@Test
	void failsWhenAListIsNotAnArray() {
		EnforcerRuleException exception = assertThrows(EnforcerRuleException.class,
				ruleFor("{ \"permissions\": { \"allow\": \"Edit\" } }")::execute);
		assertTrue(exception.getMessage().contains("'permissions.allow' must be an array"), exception.getMessage());
	}

	@Test
	void failsForANonStringEntry() {
		EnforcerRuleException exception = assertThrows(EnforcerRuleException.class,
				ruleFor("{ \"permissions\": { \"allow\": [ 42 ] } }")::execute);
		assertTrue(exception.getMessage().contains("entry 1 must be a string"), exception.getMessage());
	}

	@Test
	void failsForABlankEntry() {
		EnforcerRuleException exception = assertThrows(EnforcerRuleException.class,
				ruleFor("{ \"permissions\": { \"ask\": [ \"  \" ] } }")::execute);
		assertTrue(exception.getMessage().contains("blank entry"), exception.getMessage());
	}

	@Test
	void failsForMalformedEntrySyntax() {
		EnforcerRuleException exception = assertThrows(EnforcerRuleException.class,
				ruleFor("{ \"permissions\": { \"allow\": [ \"Bash(mvn *\" ] } }")::execute);
		assertTrue(exception.getMessage().contains("not of the form Tool or Tool(specifier)"),
				exception.getMessage());
	}

	@Test
	void failsForABlankSpecifier() {
		EnforcerRuleException exception = assertThrows(EnforcerRuleException.class,
				ruleFor("{ \"permissions\": { \"allow\": [ \"Bash( )\" ] } }")::execute);
		assertTrue(exception.getMessage().contains("blank specifier"), exception.getMessage());
	}

	@Test
	void failsForADuplicateEntry() {
		EnforcerRuleException exception = assertThrows(EnforcerRuleException.class,
				ruleFor("{ \"permissions\": { \"allow\": [ \"Edit\", \"Edit\" ] } }")::execute);
		assertTrue(exception.getMessage().contains("lists 'Edit' more than once"), exception.getMessage());
	}

	@Test
	void failsWhenAnEntryIsBothAllowedAndDenied() {
		EnforcerRuleException exception = assertThrows(EnforcerRuleException.class,
				ruleFor("{ \"permissions\": { \"allow\": [ \"Edit\" ], \"deny\": [ \"Edit\" ] } }")::execute);
		assertTrue(exception.getMessage().contains("appears in both 'allow' and 'deny'"), exception.getMessage());
	}

	@Test
	void failsForAnUnknownToolWhenAllowlistIsConfigured() {
		PermissionsFormatRule rule = ruleFor("{ \"permissions\": { \"allow\": [ \"Bsah(mvn *)\" ] } }");
		rule.setAllowedTools(List.of("Bash", "Edit"));

		EnforcerRuleException exception = assertThrows(EnforcerRuleException.class, rule::execute);
		assertTrue(exception.getMessage().contains("unknown tool 'Bsah'"), exception.getMessage());
	}

	@Test
	void exemptsMcpToolsFromTheAllowlist() {
		PermissionsFormatRule rule = ruleFor("{ \"permissions\": { \"allow\": [ \"mcp__github__get_me\" ] } }");
		rule.setAllowedTools(List.of("Bash"));

		assertDoesNotThrow(rule::execute);
	}

	@Test
	void failsForAForbiddenEntryPattern() {
		PermissionsFormatRule rule = ruleFor("{ \"permissions\": { \"allow\": [ \"Bash(*)\" ] } }");
		rule.setForbiddenEntryPatterns(List.of("Bash\\(\\*\\)"));

		EnforcerRuleException exception = assertThrows(EnforcerRuleException.class, rule::execute);
		assertTrue(exception.getMessage().contains("matches forbidden pattern"), exception.getMessage());
	}

	@Test
	void reportsAllProblemsTogether() {
		PermissionsFormatRule rule = ruleFor(
				"{ \"permissions\": { \"allow\": [ \"Edit\", \"Edit\", \"Bash(mvn *\" ] } }");

		EnforcerRuleException exception = assertThrows(EnforcerRuleException.class, rule::execute);
		assertTrue(exception.getMessage().contains("more than once"), exception.getMessage());
		assertTrue(exception.getMessage().contains("not of the form"), exception.getMessage());
	}

	private PermissionsFormatRule ruleFor(String content) {
		Path file = tempDir.resolve("settings.json");
		writeString(file, content);
		PermissionsFormatRule rule = new PermissionsFormatRule();
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
