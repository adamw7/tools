package io.github.adamw7.tools.enforcer.rule;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import org.apache.maven.enforcer.rule.api.EnforcerRuleException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * What every rule says about itself, held by the base class: the debug line that
 * makes a passing run readable, the notice that explains a downgraded warning,
 * and the guarantee that a rule built without a Maven session can log at all.
 */
class ClaudeCodeEnforcerRuleLoggingTest {

	@TempDir
	private Path tempDir;

	/**
	 * The regression that matters most: maven-enforcer injects a logger and nothing
	 * else does, so a rule that logged on an ordinary path used to throw a
	 * NullPointerException the moment anything but Maven ran it.
	 */
	@Test
	void logsWithoutALoggerInjected() {
		LoggingTestRule rule = new LoggingTestRule();

		assertDoesNotThrow(rule::execute);
	}

	@Test
	void failsWithoutALoggerInjectedOnTheViolationItFound() {
		LoggingTestRule rule = new LoggingTestRule();
		rule.addViolation("something is wrong");

		assertThrows(EnforcerRuleException.class, rule::execute);
	}

	/**
	 * maven-enforcer reports the verdict; what it cannot report is which inputs the
	 * verdict was reached on, which is the whole difference between a rule that read
	 * a document and one that passed because it was pointed at nothing.
	 */
	@Test
	void debugLogsWhatItWasPointedAtWhenItFindsNothing() {
		LoggingTestRule rule = new LoggingTestRule();
		File checked = tempDir.resolve("checked.md").toFile();
		rule.setCheckedFile(checked);
		CapturingLogger logger = new CapturingLogger();
		rule.setLog(logger);

		assertDoesNotThrow(rule::execute);
		assertTrue(logger.debugs().contains("LoggingTestRule[checkedFile=" + checked + "] found 0 violation(s)"),
				logger.debugs().toString());
	}

	@Test
	void debugLogsHowManyViolationsItFound() {
		LoggingTestRule rule = new LoggingTestRule();
		rule.addViolation("first");
		rule.addViolation("second");
		CapturingLogger logger = new CapturingLogger();
		rule.setLog(logger);

		assertThrows(EnforcerRuleException.class, rule::execute);
		assertTrue(logger.debugs().contains("LoggingTestRule[] found 2 violation(s)"), logger.debugs().toString());
	}

	/**
	 * A downgraded violation is one more {@code [WARNING]} in a build full of them,
	 * worded exactly like the failure it would have been, so it has to say both that
	 * it was tolerated and which rule to configure to stop tolerating it.
	 */
	@Test
	void warningSaysWhyItDidNotFailTheBuild() {
		LoggingTestRule rule = new LoggingTestRule();
		rule.addViolation("something is wrong");
		rule.setSeverity("warn");
		CapturingLogger logger = new CapturingLogger();
		rule.setLog(logger);

		assertDoesNotThrow(rule::execute);
		assertEquals(1, logger.warnings().size());
		String warning = logger.warnings().get(0);
		assertTrue(warning.contains("something is wrong"), warning);
		assertTrue(warning.contains("LoggingTestRule[] has severity 'warn', so the build was not failed"), warning);
	}

	@Test
	void debugLogsWhereItWroteItsReport() {
		LoggingTestRule rule = new LoggingTestRule();
		File report = tempDir.resolve("report.html").toFile();
		rule.setReportFile(report);
		CapturingLogger logger = new CapturingLogger();
		rule.setLog(logger);

		assertDoesNotThrow(rule::execute);
		assertTrue(logger.debugs().contains("LoggingTestRule[] wrote its report to " + report),
				logger.debugs().toString());
	}

	/** The count is what survived the baseline, not what the rule collected. */
	@Test
	void debugLogsThePassLeftBehindOnceTheBaselineHasSuppressedEverything() {
		LoggingTestRule rule = new LoggingTestRule();
		rule.addViolation("known problem");
		rule.setBaselineFile(tempDir.resolve("baseline.txt").toFile());
		rule.setWriteBaseline(true);
		assertDoesNotThrow(rule::execute);

		LoggingTestRule rerun = new LoggingTestRule();
		rerun.addViolation("known problem");
		rerun.setBaselineFile(tempDir.resolve("baseline.txt").toFile());
		CapturingLogger logger = new CapturingLogger();
		rerun.setLog(logger);

		assertDoesNotThrow(rerun::execute);
		assertTrue(logger.debugs().contains("LoggingTestRule[] found 0 violation(s)"), logger.debugs().toString());
		assertTrue(logger.infos().stream().anyMatch(info -> info.contains("1 violation(s) suppressed")),
				logger.infos().toString());
	}

	/**
	 * Minimal concrete rule that reports exactly the violations it is handed, so the
	 * base class's own logging can be asserted without a document to go wrong.
	 */
	private static final class LoggingTestRule extends ClaudeCodeEnforcerRule {

		private final List<String> violations = new ArrayList<>();

		/** Named so {@link #toString()} has a configured input to report. */
		private File checkedFile;

		void addViolation(String violation) {
			violations.add(violation);
		}

		void setCheckedFile(File checkedFile) {
			this.checkedFile = checkedFile;
		}

		@Override
		public void execute() throws EnforcerRuleException {
			report("Test rule is not satisfied:", List.copyOf(violations));
		}
	}
}
