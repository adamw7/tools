package io.github.adamw7.tools.enforcer.rule;

import java.io.File;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.lang.reflect.ParameterizedType;
import java.util.List;
import java.util.Objects;
import java.util.StringJoiner;

import org.apache.maven.enforcer.rule.api.AbstractEnforcerRule;
import org.apache.maven.enforcer.rule.api.EnforcerLogger;
import org.apache.maven.enforcer.rule.api.EnforcerRuleException;

import io.github.adamw7.tools.enforcer.text.MarkdownText;

/**
 * Base for every Claude Code enforcer rule. It owns the one behaviour they all
 * share: turning a collected list of violations into either a build failure or a
 * build warning, depending on the configured {@link #severity}.
 * <p>
 * The default severity is {@code error}. Setting
 * {@code <severity>warn</severity>} downgrades the same violations to a logged
 * warning, so a team can adopt a rule gradually before it is allowed to break the
 * build. Severity only governs collected structural violations; a misconfigured
 * rule (missing file or directory parameter) always fails, because that is a
 * build-setup mistake rather than a document-quality problem.
 * <p>
 * When {@code <reportFile>} is configured the same outcome is also written as a
 * self-contained HTML report — a single table pairing what failed and why with the
 * {@link #howToFix()} steps — so a build can surface the violations in a browser
 * or CI artifact. It is written whether the check passes or fails, so it always
 * reflects the latest run.
 * <p>
 * When {@code <baselineFile>} is configured, a violation already recorded in that
 * file is suppressed and only a <em>new</em> one drives the outcome. Record the
 * current violations once — {@code <writeBaseline>true</writeBaseline>} or
 * {@code -Dclaude.enforcer.writeBaseline=true}, which writes the baseline and
 * passes — then commit the file. The HTML report and the failure message reflect
 * only the un-suppressed violations.
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
	 * The directory a baseline's {@code ${basedir}} token stands for, normally
	 * configured as {@code ${project.basedir}}. When null it falls back to the
	 * build's working directory, which is only the same thing when Maven was
	 * invoked from that project's own directory.
	 */
	private File baseDir;

	/**
	 * Reports the violations as a single grouped message. In write-baseline mode it
	 * records every violation to {@link #baselineFile} and passes, writing the HTML
	 * report as the pass it is — the report always shows the un-suppressed
	 * violations, and that mode suppresses all of them. Otherwise it drops
	 * the violations already accepted by the baseline, writes the HTML report when
	 * {@link #reportFile} is configured, then throws when severity is {@code error}
	 * and a new violation remains, logs a warning when severity is {@code warn}, or
	 * does nothing when no new violation remains.
	 */
	protected final void report(String header, List<String> violations) throws EnforcerRuleException {
		if (isWriteBaselineRequested()) {
			recordBaseline(violations);
			writeReport(header, List.of());
			return;
		}
		Baseline baseline = Baseline.read(baselineFile, baseDir);
		List<String> newViolations = baseline.newViolations(violations);
		writeReport(header, newViolations);
		logSuppressed(violations.size() - newViolations.size());
		logStaleEntries(baseline.staleEntries(violations));
		logOutcome(newViolations);
		if (newViolations.isEmpty()) {
			return;
		}
		String message = format(header, newViolations);
		if (isWarn()) {
			log().warn(message + System.lineSeparator() + downgradeNotice());
		} else {
			throw new EnforcerRuleException(message);
		}
	}

	/**
	 * The logger, never null. maven-enforcer injects one before it runs a rule and
	 * nothing else does, so a rule constructed directly has none;
	 * {@link SilentEnforcerLogger} stands in for it, which is what lets a rule log
	 * on any path rather than only where a Maven session is guaranteed.
	 */
	protected final EnforcerLogger log() {
		return Objects.requireNonNullElse(getLog(), SilentEnforcerLogger.INSTANCE);
	}

	/**
	 * One line per rule naming what it was pointed at and what it found there.
	 * maven-enforcer already reports each rule's verdict, but by class and
	 * configured name alone: nothing in {@code Rule 8: ...McpServersValidRule
	 * (mcpServersValid) passed} distinguishes the rule that read a file from the one
	 * that passed because its optional file is absent, or because a directory
	 * parameter points somewhere empty. The rule names its own inputs through
	 * {@link #toString()}, so {@code mvn -X} shows what each verdict was reached on.
	 */
	private void logOutcome(List<String> violations) {
		log().debug(() -> this + " found " + violations.size() + " violation(s)");
	}

	/**
	 * Says why a warning did not stop the build, and which rule to configure to make
	 * it. A downgraded violation is otherwise one more {@code [WARNING]} among a
	 * build's many, worded exactly like the failure it would have been, leaving a
	 * reader to guess whether the build tolerated it deliberately.
	 */
	private String downgradeNotice() {
		return "  (" + this + " has severity 'warn', so the build was not failed)";
	}

	private boolean isWriteBaselineRequested() {
		return baselineFile != null && (writeBaseline || Boolean.getBoolean(WRITE_BASELINE_PROPERTY));
	}

	private void recordBaseline(List<String> violations) throws EnforcerRuleException {
		Baseline.write(baselineFile, violations, baseDir);
		log().info("Recorded " + violations.size() + " violation(s) to the baseline " + baselineFile);
	}

	private void logSuppressed(int suppressed) {
		if (suppressed > 0) {
			log().info(suppressed + " violation(s) suppressed by the baseline " + baselineFile);
		}
	}

	private void logStaleEntries(List<String> staleEntries) {
		if (!staleEntries.isEmpty()) {
			log().info(staleEntries.size() + " baseline entry/entries no longer match and can be removed from "
					+ baselineFile);
		}
	}

	private void writeReport(String header, List<String> violations) throws EnforcerRuleException {
		if (reportFile == null) {
			return;
		}
		new HtmlReport(header, violations, howToFix()).writeTo(reportFile);
		log().debug(() -> this + " wrote its report to " + reportFile);
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
	 * Fails when a required input file was not configured or does not exist, the
	 * pair of build-setup checks every rule runs before reading a file. The
	 * {@code name} names it in both messages.
	 */
	protected final void requireDocument(File file, String name) throws EnforcerRuleException {
		requireConfigured(file, name);
		requireExists(file, name);
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

	/**
	 * Names the rule by the inputs it was configured with: every {@code File} or
	 * {@code List<File>} parameter the concrete rule declares, from the most derived
	 * class up, skipping the ones left unset and the shared reporting parameters
	 * declared here. Maven identifies a rule in its log by this string, so what
	 * matters is which files the failing configuration pointed at — deriving that
	 * from the parameters themselves saves every rule repeating the same override.
	 */
	@Override
	public final String toString() {
		StringJoiner parameters = new StringJoiner(", ", getClass().getSimpleName() + "[", "]");
		for (Class<?> type = getClass(); type != ClaudeCodeEnforcerRule.class; type = type.getSuperclass()) {
			appendParameters(type, parameters);
		}
		return parameters.toString();
	}

	private void appendParameters(Class<?> type, StringJoiner parameters) {
		for (Field field : type.getDeclaredFields()) {
			appendParameter(field, parameters);
		}
	}

	private void appendParameter(Field field, StringJoiner parameters) {
		Object value = isFileParameter(field) ? valueOf(field) : null;
		if (value != null) {
			parameters.add(field.getName() + "=" + value);
		}
	}

	private static boolean isFileParameter(Field field) {
		return !field.isSynthetic() && !Modifier.isStatic(field.getModifiers())
				&& (field.getType() == File.class || isFileList(field));
	}

	private static boolean isFileList(Field field) {
		return field.getType() == List.class
				&& field.getGenericType() instanceof ParameterizedType parameterized
				&& parameterized.getActualTypeArguments()[0] == File.class;
	}

	/** Null when the field cannot be read, since a log label must never break a build. */
	private Object valueOf(Field field) {
		try {
			field.setAccessible(true);
			return field.get(this);
		} catch (ReflectiveOperationException | RuntimeException e) {
			log().debug("Could not read rule parameter " + field.getName() + ": " + e.getMessage());
			return null;
		}
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

	public void setBaseDir(File baseDir) {
		this.baseDir = baseDir;
	}
}
