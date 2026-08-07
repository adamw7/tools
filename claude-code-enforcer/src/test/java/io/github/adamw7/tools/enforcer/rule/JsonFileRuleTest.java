package io.github.adamw7.tools.enforcer.rule;

import static io.github.adamw7.tools.enforcer.rule.TestFiles.writeString;
import static io.github.adamw7.tools.test.ExpectedFailures.assertFailure;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.io.IOException;
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
		assertFailure(EnforcerRuleException.class, new TestJsonRule()::execute,
				"The testFile parameter is not configured");
	}

	@Test
	void failsWhenFileIsMissingByDefault() {
		TestJsonRule rule = new TestJsonRule();
		rule.setFile(tempDir.resolve("absent.json").toFile());

		assertFailure(EnforcerRuleException.class, rule::execute, "test.json does not exist at");
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
		assertFailure(EnforcerRuleException.class, ruleFor("   ")::execute, "test.json is empty");
	}

	@Test
	void reportsMalformedJsonAsAViolationWithoutDispatchingToCollect() {
		TestJsonRule rule = ruleFor("{ \"broken\": ");

		assertFailure(EnforcerRuleException.class, rule::execute, "test.json is not valid JSON");
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

		assertFailure(EnforcerRuleException.class, rule::execute, "test.json is not well formed:",
				"something is wrong");
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

	@Test
	void writesAnHtmlReportOfTheViolationsWhenAReportFileIsConfigured() throws IOException {
		TestJsonRule rule = ruleFor("{ }");
		rule.addViolation("something is wrong");
		File report = tempDir.resolve("report.html").toFile();
		rule.setReportFile(report);

		assertThrows(EnforcerRuleException.class, rule::execute);
		String html = Files.readString(report.toPath());
		assertTrue(html.contains("Check failed"), html);
		assertTrue(html.contains("something is wrong"), html);
		assertTrue(html.contains("How to fix"), html);
	}

	@Test
	void writesAPassedHtmlReportWhenThereAreNoViolations() throws IOException {
		TestJsonRule rule = ruleFor("{ \"name\": \"value\" }");
		File report = tempDir.resolve("report.html").toFile();
		rule.setReportFile(report);

		assertDoesNotThrow(rule::execute);
		String html = Files.readString(report.toPath());
		assertTrue(html.contains("Check passed"), html);
	}

	@Test
	void anAbsentOptionalFileRefreshesTheReportRatherThanLeavingTheLastFailure() throws IOException {
		File report = tempDir.resolve("report.html").toFile();
		TestJsonRule failing = ruleFor("{ }");
		failing.addViolation("something is wrong");
		failing.setReportFile(report);
		assertThrows(EnforcerRuleException.class, failing::execute);

		Files.delete(tempDir.resolve("test.json"));
		TestJsonRule optional = new TestJsonRule();
		optional.setOptional(true);
		optional.setFile(tempDir.resolve("test.json").toFile());
		optional.setReportFile(report);

		assertDoesNotThrow(optional::execute);
		String html = Files.readString(report.toPath());
		assertTrue(html.contains("Check passed"), html);
		assertFalse(html.contains("Check failed"), html);
		assertFalse(html.contains("something is wrong"), html);
	}

	private TestJsonRule ruleFor(String content) {
		Path file = tempDir.resolve("test.json");
		writeString(file, content);
		TestJsonRule rule = new TestJsonRule();
		rule.setFile(file.toFile());
		return rule;
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

		TestJsonRule() {
			super("testFile", "test.json");
		}

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
