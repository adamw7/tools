package io.github.adamw7.tools.enforcer.rule;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.lang.reflect.ParameterizedType;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.StringJoiner;

import javax.inject.Named;

import org.apache.maven.enforcer.rule.api.AbstractEnforcerRule;
import org.apache.maven.enforcer.rule.api.EnforcerLogger;
import org.apache.maven.enforcer.rule.api.EnforcerRuleException;

import io.github.adamw7.tools.markdown.MarkdownText;

/**
 * Base for every Claude Code enforcer rule. It owns the one behaviour they all
 * share: turning a collected list of violations into either a build failure or a
 * build warning, depending on the configured {@link #severity}.
 * <p>
 * The default severity is {@code error}. Setting
 * {@code <severity>warn</severity>} downgrades the same violations to a logged
 * warning, so a team can adopt a rule gradually before it is allowed to break the
 * build. Anything that is not one of those two is refused rather than read as the
 * default; see {@link Severity}. Severity only governs collected structural
 * violations; a misconfigured rule (missing file or directory parameter, an
 * unreadable severity) always fails, because that is a build-setup mistake rather
 * than a document-quality problem.
 * <p>
 * When {@code <reportFile>} is configured the same outcome is also written as a
 * self-contained HTML report — a single table pairing what failed and why with the
 * {@link #howToFix()} steps — so a build can surface the violations in a browser
 * or CI artifact. It is written whether the check passes or fails, so it always
 * reflects the latest run.
 * <p>
 * All three of those parameters are usually the same answer for every rule a
 * project wires, so each also has a build-wide default — {@code
 * -Dclaude.enforcer.severity}, {@code -Dclaude.enforcer.reportDir}, {@code
 * -Dclaude.enforcer.baselineDir}. See {@link RuleDefaults}. A directory names each
 * rule's file after the rule, and the reports written into one gain an
 * {@link ReportIndex index page} linking them.
 * <p>
 * When {@code <baselineFile>} is configured, a violation already recorded in that
 * file is suppressed and only a <em>new</em> one drives the outcome. Record the
 * current violations once — {@code <writeBaseline>true</writeBaseline>} or
 * {@code -Dclaude.enforcer.writeBaseline=true}, which writes the baseline and
 * passes — then commit the file. The HTML report and the failure message reflect
 * only the un-suppressed violations.
 * <p>
 * A rule's configuration parameters are its <em>fields</em>: Plexus binds a
 * {@code <claudeMdFile>} element to a field of that name, searching the class and
 * its superclasses, and it never sees the package-private setters here because it
 * looks only at public methods. So every concrete rule declares a field named
 * exactly as its pom element, and a base class that needs it reads it back through
 * an abstract accessor — {@link MarkdownFormatRule#documentFile()},
 * {@link JsonFileRule#jsonFile()}. Hoisting those fields into a base under one
 * shared name compiles, unit-tests green, and then fails every real build with
 * "Cannot find 'claudeMdFile' in class"; the {@code *IT}s are what catch it.
 */
public abstract class ClaudeCodeEnforcerRule extends AbstractEnforcerRule {

	private static final String WRITE_BASELINE_PROPERTY = "claude.enforcer.writeBaseline";

	/**
	 * Optional override: {@code error} (default) fails the build, {@code warn} only
	 * logs. Anything else is refused; see {@link Severity}. When unset the build's
	 * {@link RuleDefaults#SEVERITY_PROPERTY} decides.
	 */
	private String severity;

	/**
	 * Optional path for an HTML report of the outcome. When unset the build's
	 * {@link RuleDefaults#REPORT_DIR_PROPERTY} decides; when neither names one, no
	 * report is written.
	 */
	private File reportFile;

	/**
	 * Optional path of recorded violations to suppress. When unset the build's
	 * {@link RuleDefaults#BASELINE_DIR_PROPERTY} decides; when neither names one,
	 * nothing is suppressed.
	 */
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
		Severity configured = severity();
		File baseline = baselineFile();
		if (isWriteBaselineRequested(baseline)) {
			recordBaseline(baseline, violations);
			writeReport(header, List.of());
			return;
		}
		Baseline accepted = Baseline.read(baseline, baseDir);
		List<String> newViolations = accepted.newViolations(violations);
		writeReport(header, newViolations);
		logSuppressed(baseline, violations.size() - newViolations.size());
		logStaleEntries(baseline, accepted.staleEntries(violations));
		logOutcome(newViolations);
		if (newViolations.isEmpty()) {
			return;
		}
		String message = format(header, newViolations);
		if (configured == Severity.WARN) {
			log().warn(message + System.lineSeparator() + downgradeNotice());
		} else {
			throw new EnforcerRuleException(message);
		}
	}

	/**
	 * The severity this rule reports at: the one it was configured with, the one
	 * {@link RuleDefaults#SEVERITY_PROPERTY} sets for the whole build, or
	 * {@link Severity#DEFAULT}. It is read at the top of {@link #report} rather than
	 * where the outcome is decided, so a value that is not a severity is refused
	 * before the rule writes a report or a baseline off the back of it.
	 */
	private Severity severity() throws EnforcerRuleException {
		Optional<Severity> onTheRule = Severity.parse(severity, toString());
		if (onTheRule.isPresent()) {
			return onTheRule.get();
		}
		return Severity.parse(RuleDefaults.severity(), RuleDefaults.SEVERITY_PROPERTY).orElse(Severity.DEFAULT);
	}

	/**
	 * @return the baseline this rule reads and records, which is the one it was
	 *         configured with or the one {@link RuleDefaults} derives from the
	 *         build's baseline directory, and null when neither names one
	 */
	private File baselineFile() {
		return baselineFile != null ? baselineFile : RuleDefaults.baselineFile(ruleName()).orElse(null);
	}

	/**
	 * @return the report this rule writes, which is the one it was configured with or
	 *         the one {@link RuleDefaults} derives from the build's report directory,
	 *         and null when neither names one
	 */
	private File reportFile() {
		return reportFile != null ? reportFile : RuleDefaults.reportFile(ruleName()).orElse(null);
	}

	/**
	 * The name the pom configures this rule under, taken from the {@link Named}
	 * annotation maven-enforcer already resolves it by, so a report and a baseline
	 * derived from a directory are named the same thing the build's configuration
	 * calls the rule. A rule without the annotation — only the test doubles here —
	 * falls back to its class name, which is unique for the same reason.
	 */
	final String ruleName() {
		Named named = getClass().getAnnotation(Named.class);
		if (named == null || named.value().isBlank()) {
			return getClass().getSimpleName();
		}
		return named.value();
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
		return "  (" + this + " has severity '" + Severity.WARN.configuredName()
				+ "', so the build was not failed)";
	}

	/**
	 * Whether this run is recording a baseline rather than checking against one.
	 *
	 * <p>Asking for one without a baseline to write it to is refused rather than
	 * ignored. It used to be read as "no baseline, so check normally", which is the
	 * answer to a question nobody asked: the operator who passed
	 * {@code -Dclaude.enforcer.writeBaseline=true} was told the build failed on the
	 * very violations they had just asked to accept, with nothing to say the request
	 * had gone nowhere. Like every other build-setup mistake this fails whatever the
	 * severity.
	 */
	private boolean isWriteBaselineRequested(File baseline) throws EnforcerRuleException {
		boolean requested = writeBaseline || Boolean.getBoolean(WRITE_BASELINE_PROPERTY);
		if (requested && baseline == null) {
			throw new EnforcerRuleException("Recording a baseline was requested for " + this
					+ ", which has no baselineFile to record it to. Configure <baselineFile> on the rule or set -D"
					+ RuleDefaults.BASELINE_DIR_PROPERTY + " for the build, then re-run.");
		}
		return requested;
	}

	private void recordBaseline(File baseline, List<String> violations) throws EnforcerRuleException {
		Baseline.write(baseline, violations, baseDir);
		log().info("Recorded " + violations.size() + " violation(s) to the baseline " + baseline);
	}

	private void logSuppressed(File baseline, int suppressed) {
		if (suppressed > 0) {
			log().info(suppressed + " violation(s) suppressed by the baseline " + baseline);
		}
	}

	private void logStaleEntries(File baseline, List<String> staleEntries) {
		if (!staleEntries.isEmpty()) {
			log().info(staleEntries.size() + " baseline entry/entries no longer match and can be removed from "
					+ baseline);
		}
	}

	private void writeReport(String header, List<String> violations) throws EnforcerRuleException {
		File report = reportFile();
		if (report == null) {
			return;
		}
		new HtmlReport(header, violations, howToFix()).writeTo(report);
		log().debug(() -> this + " wrote its report to " + report);
		indexReport(violations.size());
	}

	/**
	 * Adds this rule's outcome to the index beside the other rules' reports, when the
	 * build collects them into one directory. A rule that was pointed at its own
	 * {@code reportFile} is not indexed: nothing says that file sits with anyone
	 * else's, and writing an index into a directory the build only asked for one
	 * report in would be inventing an artifact.
	 *
	 * <p>A failure here is logged rather than thrown. The verdict is already decided
	 * and the rule's own report already written; a build must not go red because the
	 * page linking the reports could not be refreshed.
	 */
	private void indexReport(int violations) {
		RuleDefaults.reportDirectory().ifPresent(directory -> index(directory, violations));
	}

	private void index(File directory, int violations) {
		try {
			ReportIndex.record(directory, ruleName(), violations);
		} catch (IOException | RuntimeException e) {
			log().warn("Could not update the report index in " + directory + ": " + e.getMessage());
		}
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
	 * Reads a required input file and fails when it cannot be decoded as UTF-8 text.
	 * Returns the content with any leading byte-order mark stripped.
	 * <p>
	 * Decoding is a build-setup mistake rather than a content problem, so it always
	 * fails regardless of {@link #severity} — but it fails as a <em>rule verdict</em>
	 * naming the file. Reading through {@link MarkdownText#read} instead let an
	 * {@link java.io.UncheckedIOException} escape the rule and abort the whole build
	 * as an internal error, which is the one outcome a rule must not have: a
	 * {@code .gitignore} carrying a single Latin-1 byte took the build down with a
	 * stack trace instead of the verdict the rule exists to give.
	 * <p>
	 * The read goes through {@link DocumentCache}, so the four rules that each check
	 * a section of {@code settings.json} read it once between them.
	 */
	protected final String requireText(File file, String description) throws EnforcerRuleException {
		return DocumentCache.text(file, () -> MarkdownText.readIfText(file))
				.orElseThrow(() -> new EnforcerRuleException(
						description + " cannot be read as UTF-8 text: " + file));
	}

	/**
	 * Reads a required input file and fails when it is blank. An empty file is a
	 * build-setup mistake, so this always fails regardless of {@link #severity}.
	 * Returns the file content (with any leading byte-order mark stripped) so the
	 * caller can validate it further. The {@code description} names the file in
	 * the message. A file that cannot be decoded fails through {@link #requireText}.
	 */
	protected final String requireContent(File file, String description) throws EnforcerRuleException {
		String content = requireText(file, description);
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
			Arrays.stream(type.getDeclaredFields()).filter(ClaudeCodeEnforcerRule::isFileParameter)
					.forEach(field -> appendParameter(field, parameters));
		}
		return parameters.toString();
	}

	private void appendParameter(Field field, StringJoiner parameters) {
		Object value = valueOf(field);
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
