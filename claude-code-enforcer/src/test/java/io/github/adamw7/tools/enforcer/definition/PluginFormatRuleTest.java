package io.github.adamw7.tools.enforcer.definition;

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
		EnforcerRuleException exception = assertThrows(EnforcerRuleException.class, new PluginFormatRule()::execute);
		assertTrue(exception.getMessage().contains("not configured"), exception.getMessage());
	}

	@Test
	void failsWhenTheManifestIsMalformedJson() {
		EnforcerRuleException exception = assertThrows(EnforcerRuleException.class, ruleFor("{ \"name\": ")::execute);
		assertTrue(exception.getMessage().contains("not valid JSON"), exception.getMessage());
	}

	@Test
	void failsWhenARequiredKeyIsMissing() {
		EnforcerRuleException exception = assertThrows(EnforcerRuleException.class,
				ruleFor("{ \"version\": \"1.0.0\" }")::execute);
		assertTrue(exception.getMessage().contains("missing required key 'name'"), exception.getMessage());
	}

	@Test
	void failsWhenTheNameBreaksConvention() {
		EnforcerRuleException exception = assertThrows(EnforcerRuleException.class,
				ruleFor("{ \"name\": \"My Plugin\" }")::execute);
		assertTrue(exception.getMessage().contains("lower-case kebab-case"), exception.getMessage());
	}

	@Test
	void failsForAMalformedVersion() {
		EnforcerRuleException exception = assertThrows(EnforcerRuleException.class,
				ruleFor("{ \"name\": \"my-plugin\", \"version\": \"one\" }")::execute);
		assertTrue(exception.getMessage().contains("not a dotted version number"), exception.getMessage());
	}

	@Test
	void allowsAPreReleaseVersionSuffix() {
		assertDoesNotThrow(ruleFor("{ \"name\": \"my-plugin\", \"version\": \"1.0.0-beta.1\" }")::execute);
	}

	@Test
	void failsForABlankDescription() {
		EnforcerRuleException exception = assertThrows(EnforcerRuleException.class,
				ruleFor("{ \"name\": \"my-plugin\", \"description\": \"  \" }")::execute);
		assertTrue(exception.getMessage().contains("description must not be empty"), exception.getMessage());
	}

	@Test
	void reportsUnknownKeysWhenAllowedKeysIsConfigured() {
		PluginFormatRule rule = ruleFor("{ \"name\": \"my-plugin\", \"descripton\": \"typo\" }");
		rule.setAllowedKeys(List.of("name", "version", "description", "author"));

		EnforcerRuleException exception = assertThrows(EnforcerRuleException.class, rule::execute);
		assertTrue(exception.getMessage().contains("unknown key 'descripton'"), exception.getMessage());
	}

	@Test
	void supportsCustomRequiredKeys() {
		PluginFormatRule rule = ruleFor("{ \"name\": \"my-plugin\" }");
		rule.setRequiredKeys(List.of("name", "description"));

		EnforcerRuleException exception = assertThrows(EnforcerRuleException.class, rule::execute);
		assertTrue(exception.getMessage().contains("missing required key 'description'"), exception.getMessage());
	}

	private PluginFormatRule ruleFor(String content) {
		Path file = tempDir.resolve("plugin.json");
		writeString(file, content);
		PluginFormatRule rule = new PluginFormatRule();
		rule.setPluginFile(file.toFile());
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
