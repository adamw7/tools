package io.github.adamw7.tools.enforcer.settings;

import static io.github.adamw7.tools.test.TestFiles.writeString;
import static io.github.adamw7.tools.test.ExpectedFailures.assertFailure;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

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
		assertFailure(EnforcerRuleException.class, new PermissionsFormatRule()::execute, "not configured");
	}

	@Test
	void failsWhenPermissionsIsNotAnObject() {
		assertFailure(EnforcerRuleException.class, ruleFor("{ \"permissions\": [] }")::execute,
				"must be a JSON object");
	}

	@Test
	void failsWhenAListIsNotAnArray() {
		assertFailure(EnforcerRuleException.class, ruleFor("{ \"permissions\": { \"allow\": \"Edit\" } }")::execute,
				"'permissions.allow' must be an array");
	}

	@Test
	void failsForANonStringEntry() {
		assertFailure(EnforcerRuleException.class, ruleFor("{ \"permissions\": { \"allow\": [ 42 ] } }")::execute,
				"entry 1 must be a string");
	}

	@Test
	void failsForABlankEntry() {
		assertFailure(EnforcerRuleException.class, ruleFor("{ \"permissions\": { \"ask\": [ \"  \" ] } }")::execute,
				"blank entry");
	}

	@Test
	void failsForMalformedEntrySyntax() {
		assertFailure(EnforcerRuleException.class,
				ruleFor("{ \"permissions\": { \"allow\": [ \"Bash(mvn *\" ] } }")::execute,
				"not of the form Tool or Tool(specifier)");
	}

	@Test
	void failsForABlankSpecifier() {
		assertFailure(EnforcerRuleException.class,
				ruleFor("{ \"permissions\": { \"allow\": [ \"Bash( )\" ] } }")::execute, "blank specifier");
	}

	@Test
	void failsForADuplicateEntry() {
		assertFailure(EnforcerRuleException.class,
				ruleFor("{ \"permissions\": { \"allow\": [ \"Edit\", \"Edit\" ] } }")::execute,
				"lists 'Edit' more than once");
	}

	@Test
	void failsWhenAnEntryIsBothAllowedAndDenied() {
		assertFailure(EnforcerRuleException.class,
				ruleFor("{ \"permissions\": { \"allow\": [ \"Edit\" ], \"deny\": [ \"Edit\" ] } }")::execute,
				"appears in both 'allow' and 'deny'");
	}

	@Test
	void failsForAnUnknownToolWhenAllowlistIsConfigured() {
		PermissionsFormatRule rule = ruleFor("{ \"permissions\": { \"allow\": [ \"Bsah(mvn *)\" ] } }");
		rule.setAllowedTools(List.of("Bash", "Edit"));

		assertFailure(EnforcerRuleException.class, rule::execute, "unknown tool 'Bsah'");
	}

	@Test
	void exemptsMcpToolsFromTheAllowlist() {
		PermissionsFormatRule rule = ruleFor("{ \"permissions\": { \"allow\": [ \"mcp__github__get_me\" ] } }");
		rule.setAllowedTools(List.of("Bash"));

		assertDoesNotThrow(rule::execute);
	}

	@Test
	void acceptsHyphenatedMcpToolNames() {
		// MCP server names routinely contain hyphens, so an entry such as
		// mcp__claude-code-remote__list_repos is valid syntax.
		PermissionsFormatRule rule = ruleFor(
				"{ \"permissions\": { \"allow\": [ \"mcp__claude-code-remote__list_repos\", \"mcp__my-server__tool(x)\" ] } }");
		rule.setAllowedTools(List.of("Bash"));

		assertDoesNotThrow(rule::execute);
	}

	@Test
	void failsForAForbiddenEntryPattern() {
		PermissionsFormatRule rule = ruleFor("{ \"permissions\": { \"allow\": [ \"Bash(*)\" ] } }");
		rule.setForbiddenEntryPatterns(List.of("Bash\\(\\*\\)"));

		assertFailure(EnforcerRuleException.class, rule::execute, "matches forbidden pattern");
	}

	@Test
	void reportsAllProblemsTogether() {
		PermissionsFormatRule rule = ruleFor(
				"{ \"permissions\": { \"allow\": [ \"Edit\", \"Edit\", \"Bash(mvn *\" ] } }");

		assertFailure(EnforcerRuleException.class, rule::execute, "more than once", "not of the form");
	}

	@Test
	void failsWithAClearMessageForAForbiddenPatternThatIsNotAValidRegularExpression() {
		PermissionsFormatRule rule = ruleFor("{ \"permissions\": { \"allow\": [ \"Bash(mvn *)\" ] } }");
		rule.setForbiddenEntryPatterns(List.of("Bash(\\*"));

		assertFailure(EnforcerRuleException.class, rule::execute, "not a valid regular expression", "Bash(\\*");
	}

	private PermissionsFormatRule ruleFor(String content) {
		Path file = tempDir.resolve("settings.json");
		writeString(file, content);
		PermissionsFormatRule rule = new PermissionsFormatRule();
		rule.setSettingsFile(file.toFile());
		return rule;
	}

}
