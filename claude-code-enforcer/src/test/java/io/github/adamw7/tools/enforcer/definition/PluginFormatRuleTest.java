package io.github.adamw7.tools.enforcer.definition;

import static io.github.adamw7.tools.enforcer.rule.TestFiles.writeString;
import static io.github.adamw7.tools.test.ExpectedFailures.assertFailure;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

import java.nio.file.Path;
import java.util.List;

import org.apache.maven.enforcer.rule.api.EnforcerRuleException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class PluginFormatRuleTest {

	private static final String VALID_MANIFEST = """
			{
			  "name": "my-plugin",
			  "version": "1.2.3",
			  "description": "Does useful things."
			}
			""";

	@TempDir
	private Path tempDir;

	@Test
	void passesForAValidManifest() {
		assertDoesNotThrow(ruleFor(VALID_MANIFEST)::execute);
	}

	@Test
	void passesWhenTheManifestIsAbsent() {
		PluginFormatRule rule = new PluginFormatRule();
		rule.setPluginFile(tempDir.resolve("absent.json").toFile());

		assertDoesNotThrow(rule::execute);
	}

	@Test
	void failsWhenNotConfigured() {
		assertFailure(EnforcerRuleException.class, new PluginFormatRule()::execute, "not configured");
	}

	@Test
	void failsWhenTheManifestIsMalformedJson() {
		assertFailure(EnforcerRuleException.class, ruleFor("{ \"name\": ")::execute, "not valid JSON");
	}

	@Test
	void failsWhenARequiredKeyIsMissing() {
		assertFailure(EnforcerRuleException.class, ruleFor("{ \"version\": \"1.0.0\" }")::execute,
				"missing required key 'name'");
	}

	@Test
	void failsWhenTheNameBreaksConvention() {
		assertFailure(EnforcerRuleException.class, ruleFor("{ \"name\": \"My Plugin\" }")::execute,
				"lower-case kebab-case");
	}

	@Test
	void failsForAMalformedVersion() {
		assertFailure(EnforcerRuleException.class,
				ruleFor("{ \"name\": \"my-plugin\", \"version\": \"one\" }")::execute, "not a dotted version number");
	}

	@Test
	void allowsAPreReleaseVersionSuffix() {
		assertDoesNotThrow(ruleFor("{ \"name\": \"my-plugin\", \"version\": \"1.0.0-beta.1\" }")::execute);
	}

	@Test
	void allowsBuildMetadataAfterAPreReleaseSuffix() {
		assertDoesNotThrow(ruleFor("{ \"name\": \"my-plugin\", \"version\": \"1.0.0-beta.1+build.5\" }")::execute);
	}

	@Test
	void failsForABlankDescription() {
		assertFailure(EnforcerRuleException.class,
				ruleFor("{ \"name\": \"my-plugin\", \"description\": \"  \" }")::execute,
				"description must not be empty");
	}

	@Test
	void reportsUnknownKeysWhenAllowedKeysIsConfigured() {
		PluginFormatRule rule = ruleFor("{ \"name\": \"my-plugin\", \"descripton\": \"typo\" }");
		rule.setAllowedKeys(List.of("name", "version", "description", "author"));

		assertFailure(EnforcerRuleException.class, rule::execute, "unknown key 'descripton'");
	}

	@Test
	void supportsCustomRequiredKeys() {
		PluginFormatRule rule = ruleFor("{ \"name\": \"my-plugin\" }");
		rule.setRequiredKeys(List.of("name", "description"));

		assertFailure(EnforcerRuleException.class, rule::execute, "missing required key 'description'");
	}

	private PluginFormatRule ruleFor(String content) {
		Path file = tempDir.resolve("plugin.json");
		writeString(file, content);
		PluginFormatRule rule = new PluginFormatRule();
		rule.setPluginFile(file.toFile());
		return rule;
	}

}
