package io.github.adamw7.tools.enforcer.rule;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import org.apache.maven.enforcer.rule.api.EnforcerRuleException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * The shared configuration every rule inherits: what a severity that is not a
 * severity does, what asking for a baseline with nowhere to write it does, and the
 * build-wide defaults that save a project spelling the same three parameters out
 * once per rule.
 */
class ClaudeCodeEnforcerRuleConfigurationTest {

	@TempDir
	private Path tempDir;

	/** Each test sets at most the properties it needs; none may outlive it. */
	@AfterEach
	void clearBuildWideDefaults() {
		System.clearProperty(RuleDefaults.SEVERITY_PROPERTY);
		System.clearProperty(RuleDefaults.REPORT_DIR_PROPERTY);
		System.clearProperty(RuleDefaults.BASELINE_DIR_PROPERTY);
	}

	/**
	 * The regression: a misspelt severity was read as 'error', so a rule the author
	 * believed downgraded failed the build in exactly the way a working rule does.
	 */
	@Test
	void refusesASeverityThatIsNotASeverity() {
		ConfigurableTestRule rule = new ConfigurableTestRule();
		rule.addViolation("something is wrong");
		rule.setSeverity("warning");

		EnforcerRuleException thrown = assertThrows(EnforcerRuleException.class, rule::execute);

		assertTrue(thrown.getMessage().contains("is not a severity"), thrown.getMessage());
		assertFalse(thrown.getMessage().contains("something is wrong"), thrown.getMessage());
	}

	/** Refused even when the rule found nothing, since the configuration is wrong either way. */
	@Test
	void refusesASeverityThatIsNotASeverityEvenWithNoViolations() {
		ConfigurableTestRule rule = new ConfigurableTestRule();
		rule.setSeverity("ignore");

		assertThrows(EnforcerRuleException.class, rule::execute);
	}

	/** A refused severity must not leave a report behind claiming a verdict was reached. */
	@Test
	void writesNoReportWhenTheSeverityIsRefused() {
		ConfigurableTestRule rule = new ConfigurableTestRule();
		rule.addViolation("something is wrong");
		rule.setSeverity("warning");
		Path report = tempDir.resolve("report.html");
		rule.setReportFile(report.toFile());

		assertThrows(EnforcerRuleException.class, rule::execute);
		assertFalse(Files.exists(report), "a refused configuration reached a verdict it had no business reaching");
	}

	/**
	 * The other silent no-op: asking to record a baseline with no baselineFile told
	 * the operator the build failed on the very violations they had asked to accept.
	 */
	@Test
	void refusesAWriteBaselineRequestWithNowhereToWriteIt() {
		ConfigurableTestRule rule = new ConfigurableTestRule();
		rule.addViolation("known problem");
		rule.setWriteBaseline(true);

		EnforcerRuleException thrown = assertThrows(EnforcerRuleException.class, rule::execute);

		assertTrue(thrown.getMessage().contains("no baselineFile"), thrown.getMessage());
		assertTrue(thrown.getMessage().contains(RuleDefaults.BASELINE_DIR_PROPERTY), thrown.getMessage());
	}

	@Test
	void buildWideSeverityDowngradesARuleThatConfiguresNone() {
		System.setProperty(RuleDefaults.SEVERITY_PROPERTY, "warn");
		ConfigurableTestRule rule = new ConfigurableTestRule();
		rule.addViolation("something is wrong");
		CapturingLogger logger = new CapturingLogger();
		rule.setLog(logger);

		assertDoesNotThrow(rule::execute);
		assertEquals(1, logger.warnings().size());
	}

	/** The property is a default, not an override: one rule may still insist. */
	@Test
	void aRuleOwnSeverityWinsOverTheBuildWideOne() {
		System.setProperty(RuleDefaults.SEVERITY_PROPERTY, "warn");
		ConfigurableTestRule rule = new ConfigurableTestRule();
		rule.addViolation("something is wrong");
		rule.setSeverity("error");

		assertThrows(EnforcerRuleException.class, rule::execute);
	}

	@Test
	void refusesABuildWideSeverityThatIsNotASeverity() {
		System.setProperty(RuleDefaults.SEVERITY_PROPERTY, "quiet");
		ConfigurableTestRule rule = new ConfigurableTestRule();

		EnforcerRuleException thrown = assertThrows(EnforcerRuleException.class, rule::execute);

		assertTrue(thrown.getMessage().contains(RuleDefaults.SEVERITY_PROPERTY), thrown.getMessage());
	}

	@Test
	void buildWideReportDirectoryNamesEachReportAfterItsRule() throws IOException {
		System.setProperty(RuleDefaults.REPORT_DIR_PROPERTY, tempDir.toString());
		ConfigurableTestRule rule = new ConfigurableTestRule();
		rule.addViolation("something is wrong");

		assertThrows(EnforcerRuleException.class, rule::execute);

		Path report = tempDir.resolve("configurableTest.html");
		assertTrue(Files.exists(report), "no report named after the rule");
		assertTrue(Files.readString(report, StandardCharsets.UTF_8).contains("something is wrong"));
	}

	/** Twenty reports are twenty files nobody opens; the index says which are worth opening. */
	@Test
	void buildWideReportDirectoryGainsAnIndexOfEveryRuleThatReported() throws IOException {
		System.setProperty(RuleDefaults.REPORT_DIR_PROPERTY, tempDir.toString());
		ConfigurableTestRule failing = new ConfigurableTestRule();
		failing.addViolation("something is wrong");
		assertThrows(EnforcerRuleException.class, failing::execute);
		assertDoesNotThrow(new OtherTestRule()::execute);

		String index = Files.readString(tempDir.resolve(ReportIndex.INDEX_FILE), StandardCharsets.UTF_8);
		assertTrue(index.contains("configurableTest.html"), index);
		assertTrue(index.contains("otherTest.html"), index);
		assertTrue(index.contains("1 of 2 rule(s) passed"), index);
	}

	@Test
	void aRuleOwnReportFileWinsOverTheBuildWideDirectory() {
		System.setProperty(RuleDefaults.REPORT_DIR_PROPERTY, tempDir.toString());
		Path chosen = tempDir.resolve("chosen.html");
		ConfigurableTestRule rule = new ConfigurableTestRule();
		rule.setReportFile(chosen.toFile());

		assertDoesNotThrow(rule::execute);
		assertTrue(Files.exists(chosen));
		assertFalse(Files.exists(tempDir.resolve("configurableTest.html")));
	}

	/**
	 * Baselines are named after the rule for the same reason reports are: one shared
	 * file would let a violation accepted for one rule suppress another's.
	 */
	@Test
	void buildWideBaselineDirectoryNamesEachBaselineAfterItsRule() {
		System.setProperty(RuleDefaults.BASELINE_DIR_PROPERTY, tempDir.toString());
		ConfigurableTestRule recording = new ConfigurableTestRule();
		recording.addViolation("known problem");
		recording.setWriteBaseline(true);
		assertDoesNotThrow(recording::execute);

		assertTrue(Files.exists(tempDir.resolve("configurableTest.txt")));

		ConfigurableTestRule rerun = new ConfigurableTestRule();
		rerun.addViolation("known problem");
		assertDoesNotThrow(rerun::execute);

		OtherTestRule otherRule = new OtherTestRule();
		otherRule.addViolation("known problem");
		assertThrows(EnforcerRuleException.class, otherRule::execute);
	}

	/**
	 * The name a report and a baseline are filed under is the one a pom configures the
	 * rule by, which the architecture tests hold every rule's @Named value to.
	 */
	@Test
	void ruleNameIsTheOneAPomConfiguresItBy() {
		assertEquals("configurableTest", new ConfigurableTestRule().ruleName());
	}

	private static class ConfigurableTestRule extends ClaudeCodeEnforcerRule {

		private final List<String> violations = new ArrayList<>();

		void addViolation(String violation) {
			violations.add(violation);
		}

		@Override
		public void execute() throws EnforcerRuleException {
			report("Test rule is not satisfied:", List.copyOf(violations));
		}
	}

	private static final class OtherTestRule extends ConfigurableTestRule {
	}
}
