package io.github.adamw7.tools.enforcer.rule;

import java.io.File;
import java.util.List;

import org.apache.maven.enforcer.rule.api.AbstractEnforcerRule;
import org.apache.maven.enforcer.rule.api.EnforcerRuleException;

import io.github.adamw7.tools.enforcer.text.MarkdownText;

/**
 * Base for every Claude Code enforcer rule. It owns the one behaviour they all
 * share: turning a collected list of violations into either a build failure or a
 * build warning, depending on the configured {@link #severity}.
 * <p>
 * The default severity is {@code error}, which fails the build and preserves the
 * original behaviour. Setting {@code <severity>warn</severity>} in the rule
 * configuration downgrades the same violations to a logged warning, so a team can
 * adopt a rule gradually before it is allowed to break the build. Severity only
 * governs collected structural violations; a misconfigured rule (missing file or
 * directory parameter) always fails, because that is a build-setup mistake rather
 * than a document-quality problem.
 * <p>
 * When {@code <reportFile>} is configured the same outcome is also written as a
 * self-contained HTML report — a single table pairing what failed and why with
 * the {@link #howToFix()} steps in a "How to fix" column — so a build can surface
 * the violations in a browser or CI artifact regardless of severity. The report is
 * written whether the check passes or fails, so it always reflects the latest run.
 * <p>
 * When {@code <baselineFile>} is configured, a violation already recorded in that
 * file is suppressed and only a <em>new</em> violation drives the outcome, so a
 * rule can be turned into an error gate without first fixing every pre-existing
 * violation. Record the current violations once — set {@code <writeBaseline>true</writeBaseline>}
 * or run the build with {@code -Dclaude.enforcer.writeBaseline=true}, which writes
 * the baseline and passes — then commit the file; from then on the backlog is
 * grandfathered and any newly introduced violation still fails. The HTML report
 * and the failure message reflect only the un-suppressed violations.
 */
public abstract class ClaudeCodeEnforcerRule extends AbstractEnforcerRule {

	private static final String WARN = "warn";
	private static final String WRITE_BASELINE_PROPERTY = "claude.enforcer.writeBaseline";

	/** Optional override: {@code error} (default) fails the build, {@code warn} only logs. */
	private String severity;

	/** Optional path for an HTML report of the outcome. When null, no report is written. */
	private File reportFile;

	/** Optional path of recorded violations to suppress. When null, nothing is suppressed. */
	private File baselineFile;

	/** When true (or the {@code claude.enforcer.writeBaseline} property is set), records the current violations. */
	private boolean writeBaseline;

	/**
	 * Reports the violations as a single grouped message. In write-baseline mode it
	 * records every violation to {@link #baselineFile} and passes. Otherwise it drops
	 * the violations already accepted by the baseline, writes the HTML report when
	 * {@link #reportFile} is configured, then throws when severity is {@code error}
	 * and a new violation remains, logs a warning when severity is {@code warn}, or
	 * does nothing when no new violation remains.
	 */
	protected final void report(String header, List<String> violations) throws EnforcerRuleException {
		if (isWriteBaselineRequested()) {
			recordBaseline(violations);
			return;
		}
		Baseline baseline = Baseline.read(baselineFile);
		List<String> newViolations = baseline.newViolations(violations);
		writeReport(header, newViolations);
		logSuppressed(violations.size() - newViolations.size());
		logStaleEntries(baseline.staleEntries(violations));
		if (newViolations.isEmpty()) {
			return;
		}
		String message = format(header, newViolations);
		if (isWarn()) {
			getLog().warn(message);
		} else {
			throw new EnforcerRuleException(message);
		}
	}

	private boolean isWriteBaselineRequested() {
		return baselineFile != null && (writeBaseline || Boolean.getBoolean(WRITE_BASELINE_PROPERTY));
	}

	private void recordBaseline(List<String> violations) throws EnforcerRuleException {
		Baseline.write(baselineFile, violations);
		getLog().info("Recorded " + violations.size() + " violation(s) to the baseline " + baselineFile);
	}

	private void logSuppressed(int suppressed) {
		if (suppressed > 0) {
			getLog().info(suppressed + " violation(s) suppressed by the baseline " + baselineFile);
		}
	}

	private void logStaleEntries(List<String> staleEntries) {
		if (!staleEntries.isEmpty()) {
			getLog().info(staleEntries.size() + " baseline entry/entries no longer match and can be removed from "
					+ baselineFile);
		}
	}

	private void writeReport(String header, List<String> violations) throws EnforcerRuleException {
		if (reportFile == null) {
			return;
		}
		new HtmlReport(header, violations, howToFix()).writeTo(reportFile);
	}

	/**
	 * The ordered remediation steps shown under "How to fix" in the HTML report.
	 * The default is generic advice; a rule with a more specific fix overrides it.
	 */
	protected List<String> howToFix() {
		return List.of(
				"Open the file named in each message above.",
				"Correct every listed item so it matches what the rule expects.",
				"Re-run the build to confirm the rule passes.");
	}

	private String format(String header, List<String> violations) {
		String separator = System.lineSeparator() + "  - ";
		return header + separator + String.join(separator, violations);
	}

	private boolean isWarn() {
		return WARN.equalsIgnoreCase(severity);
	}

	/**
	 * Fails when a required directory parameter was not configured, which is a
	 * build-setup mistake rather than a content problem and so always fails
	 * regardless of {@link #severity}.
	 */
	protected final void requireConfigured(Object parameter, String name) throws EnforcerRuleException {
		if (parameter == null) {
			throw new EnforcerRuleException("The " + name + " parameter is not configured");
		}
	}

	/**
	 * Fails when a required input file does not exist on disk. Like a missing
	 * parameter this is a build-setup mistake, so it always fails regardless of
	 * {@link #severity}. The {@code description} names the file in the message,
	 * e.g. {@code CLAUDE.md} or {@code settings.json}.
	 */
	protected final void requireExists(File file, String description) throws EnforcerRuleException {
		if (!file.isFile()) {
			throw new EnforcerRuleException(description + " does not exist at " + file);
		}
	}

	/**
	 * Reads a required input file and fails when it is blank. An empty file is a
	 * build-setup mistake, so this always fails regardless of {@link #severity}.
	 * Returns the file content (with any leading byte-order mark stripped) so the
	 * caller can validate it further. The {@code description} names the file in
	 * the message.
	 */
	protected final String requireContent(File file, String description) throws EnforcerRuleException {
		String content = MarkdownText.read(file, description);
		if (content.isBlank()) {
			throw new EnforcerRuleException(description + " is empty: " + file);
		}
		return content;
	}

	public void setSeverity(String severity) {
		this.severity = severity;
	}

	public void setReportFile(File reportFile) {
		this.reportFile = reportFile;
	}

	public void setBaselineFile(File baselineFile) {
		this.baselineFile = baselineFile;
	}

	public void setWriteBaseline(boolean writeBaseline) {
		this.writeBaseline = writeBaseline;
	}
}
