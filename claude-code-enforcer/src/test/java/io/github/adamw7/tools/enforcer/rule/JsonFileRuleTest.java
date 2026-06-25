package io.github.adamw7.tools.enforcer.rule;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.databind.JsonNode;

import org.apache.maven.enforcer.rule.api.EnforcerRuleException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class JsonFileRuleTest {

	@TempDir
	private Path tempDir;

	@Test
	void failsWhenFileIsNotConfigured() {
		EnforcerRuleException exception = assertThrows(EnforcerRuleException.class, new TestJsonRule()::execute);
		assertTrue(exception.getMessage().contains("The testFile parameter is not configured"), exception.getMessage());
	}

	@Test
	void failsWhenFileIsMissingByDefault() {
		TestJsonRule rule = new TestJsonRule();
		rule.setFile(tempDir.resolve("absent.json").toFile());

		EnforcerRuleException exception = assertThrows(EnforcerRuleException.class, rule::execute);
		assertTrue(exception.getMessage().contains("test.json does not exist at"), exception.getMessage());
	}

	@Test
	void passesWhenAnOptionalFileIsMissing() {
		TestJsonRule rule = new TestJsonRule();
		rule.setOptional(true);
		rule.setFile(tempDir.resolve("absent.json").toFile());

		assertDoesNotThrow(rule::execute);
		assertNull(rule.parsedRoot, "collectViolations must not run when the file is absent");
	}

	@Test
	void failsWhenFileIsEmpty() {
		EnforcerRuleException exception = assertThrows(EnforcerRuleException.class, ruleFor("   ")::execute);
		assertTrue(exception.getMessage().contains("test.json is empty"), exception.getMessage());
	}

	@Test
	void reportsMalformedJsonAsAViolationWithoutDispatchingToCollect() {
		TestJsonRule rule = ruleFor("{ \"broken\": ");

		EnforcerRuleException exception = assertThrows(EnforcerRuleException.class, rule::execute);
		assertTrue(exception.getMessage().contains("test.json is not valid JSON"), exception.getMessage());
		assertNull(rule.parsedRoot, "collectViolations must not run when parsing fails");
	}

	@Test
	void passesValidJsonAndPassesTheParsedObjectToCollect() {
		TestJsonRule rule = ruleFor("{ \"name\": \"value\" }");

		assertDoesNotThrow(rule::execute);
		assertNotNull(rule.parsedRoot, "collectViolations must receive the parsed object");
		assertEquals("value", rule.parsedRoot.get("name").asText());
	}

	@Test
	void reportsCollectedViolationsUnderTheHeader() {
		TestJsonRule rule = ruleFor("{ }");
		rule.addViolation("something is wrong");

		EnforcerRuleException exception = assertThrows(EnforcerRuleException.class, rule::execute);
		assertTrue(exception.getMessage().contains("test.json is not well formed:"), exception.getMessage());
		assertTrue(exception.getMessage().contains("something is wrong"), exception.getMessage());
	}

	@Test
	void warnSeverityDowngradesCollectedViolationsToAWarning() {
		TestJsonRule rule = ruleFor("{ }");
		rule.addViolation("something is wrong");
		rule.setSeverity("warn");
		CapturingLogger logger = new CapturingLogger();
		rule.setLog(logger);

		assertDoesNotThrow(rule::execute);
		assertEquals(1, logger.warnings().size());
		assertTrue(logger.warnings().get(0).contains("something is wrong"), logger.warnings().get(0));
	}

	private TestJsonRule ruleFor(String content) {
		Path file = tempDir.resolve("test.json");
		writeString(file, content);
		TestJsonRule rule = new TestJsonRule();
		rule.setFile(file.toFile());
		return rule;
	}

	private static void writeString(Path file, String content) {
		try {
			Files.writeString(file, content);
		} catch (IOException e) {
			throw new UncheckedIOException("Could not write " + file, e);
		}
	}

	/**
	 * Minimal concrete rule that records what the base class hands to
	 * {@link #collectViolations} so the shared scaffolding can be asserted in
	 * isolation from any real document checks.
	 */
	private static final class TestJsonRule extends JsonFileRule {

		private File file;
		private boolean optional;
		private final List<String> injectedViolations = new ArrayList<>();
		private JsonNode parsedRoot;

		void setFile(File file) {
			this.file = file;
		}

		void setOptional(boolean optional) {
			this.optional = optional;
		}

		void addViolation(String violation) {
			injectedViolations.add(violation);
		}

		@Override
		protected File jsonFile() {
			return file;
		}

		@Override
		protected String fileParameter() {
			return "testFile";
		}

		@Override
		protected String description() {
			return "test.json";
		}

		@Override
		protected String header() {
			return "test.json is not well formed:";
		}

		@Override
		protected void handleMissingFile(File missing) throws EnforcerRuleException {
			if (!optional) {
				super.handleMissingFile(missing);
			}
		}

		@Override
		protected void collectViolations(JsonNode root, List<String> violations) {
			this.parsedRoot = root;
			violations.addAll(injectedViolations);
		}
	}
}
