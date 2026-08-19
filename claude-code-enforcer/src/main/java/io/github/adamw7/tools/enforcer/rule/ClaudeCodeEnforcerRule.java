package io.github.adamw7.tools.enforcer.rule;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.lang.reflect.ParameterizedType;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.StringJoiner;

import org.apache.maven.enforcer.rule.api.AbstractEnforcerRule;
import org.apache.maven.enforcer.rule.api.EnforcerLogger;
import org.apache.maven.enforcer.rule.api.EnforcerRuleException;

import io.github.adamw7.tools.markdown.MarkdownText;

/**
 * Base for every Claude Code enforcer rule. It owns the one behaviour they all
 * share: turning a collected list of violations into a build failure or a build
 * warning, depending on the configured {@link #severity}.
 * <p>
 * The default severity is {@code error}. {@code <severity>warn</severity>}
 * downgrades the same violations to a logged warning, so a team can adopt a rule
 * before it is allowed to break the build; anything else is refused rather than
 * read as the default (see {@link Severity}). Severity governs only collected
 * structural violations — a misconfigured rule (missing parameter, unreadable
 * severity) always fails, being a build-setup mistake rather than a
 * document-quality problem.
 * <p>
 * {@code <reportFile>} also writes the outcome as a self-contained HTML report
 * pairing what failed with the {@link #howToFix()} steps, written whether the
 * check passes or fails so it always reflects the latest run.
 * {@code <baselineFile>} suppresses a violation already recorded in that file so
 * only a <em>new</em> one drives the outcome; record the current violations once
 * with {@code <writeBaseline>true</writeBaseline>}, then commit the file. All
 * three parameters also have build-wide defaults —
 * {@code -Dclaude.enforcer.severity}, {@code -Dclaude.enforcer.reportDir},
 * {@code -Dclaude.enforcer.baselineDir}; see {@link RuleDefaults}. Reports written
 * into one directory gain an {@link ReportIndex index page} linking them.
 * <p>
 * A rule's configuration parameters are its <em>fields</em>: Plexus binds a
 * {@code <claudeMdFile>} element to a field of that name and never sees the
 * package-private setters here, because it looks only at public methods. So every
 * concrete rule declares a field named exactly as its pom element, and a base that
 * needs it reads it back through an abstract accessor —
 * {@link MarkdownFormatRule#documentFile()}, {@link JsonFileRule#jsonFile()}.
 * Hoisting those fields into a base under one shared name compiles, unit-tests
 * green, then fails every real build with "Cannot find 'claudeMdFile' in class";
 * the {@code *IT}s are what catch it.
 */
public abstract class ClaudeCodeEnforcerRule extends AbstractEnforcerRule {

	private static final String WRITE_BASELINE_PROPERTY = "claude.enforcer.writeBaseline";

	/** Stripped from a class name to derive the name a pom configures the rule under. */
	private static final String RULE_SUFFIX = "Rule";

	/**
	 * Optional override: {@code error} (default) fails the build, {@code warn} only
	 * logs. When unset {@link RuleDefaults#SEVERITY_PROPERTY} decides.
	 */
	private String severity;

	/**
	 * Optional path for an HTML report of the outcome. When unset
	 * {@link RuleDefaults#REPORT_DIR_PROPERTY} decides; when neither names one, none
	 * is written.
	 */
	private File reportFile;

	/**
	 * Optional path of recorded violations to suppress. When unset
	 * {@link RuleDefaults#BASELINE_DIR_PROPERTY} decides; when neither names one,
	 * nothing is suppressed.
	 */
	private File baselineFile;

	/** Records the current violations instead of checking against them. */
	private boolean writeBaseline;

	/**
	 * The directory a baseline's {@code ${basedir}} token stands for, normally
	 * {@code ${project.basedir}}. Null falls back to the build's working directory,
	 * which is the same thing only when Maven was invoked from that project.
	 */
	private File baseDir;

	/**
	 * Set when a composite rule runs this one as one of its parts, in which case the
	 * violations are handed over instead of decided on here: the composite reports
	 * every part's findings together, under its own severity, baseline and report.
	 * Null for a rule a pom configured directly, which is every rule by default.
	 */
	private ViolationSink sink;

	/**
	 * Where a rule run as part of a composite sends what it found. It is given the
	 * rule's name as well as its violations, because a composite's report has to say
	 * which part each violation came from — the name a reader takes back to the
	 * catalogue, and the name they would configure to switch the part off.
	 */
	@FunctionalInterface
	public interface ViolationSink {
		void accept(String ruleName, List<String> violations);
	}

	/**
	 * Runs this rule as part of {@code sink}'s composite rather than on its own
	 * account. A rule collects its violations the same way either way; what changes
	 * is only who decides what they mean.
	 */
	public final void reportTo(ViolationSink sink) {
		this.sink = sink;
	}

	/**
	 * Reports the violations as a single grouped message. In write-baseline mode it
	 * records every violation and passes, writing the HTML report as the pass it is.
	 * Otherwise it drops the violations the baseline already accepted, writes the
	 * report, then throws when severity is {@code error} and a new violation remains,
	 * logs a warning when severity is {@code warn}, or does nothing.
	 */
	protected final void report(String header, List<String> violations) throws EnforcerRuleException {
		if (sink != null) {
			sink.accept(ruleName(), violations);
			return;
		}
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
	 * The severity this rule reports at, read at the top of {@link #report} rather
	 * than where the outcome is decided, so a value that is not a severity is refused
	 * before the rule writes a report or a baseline off the back of it.
	 */
	private Severity severity() throws EnforcerRuleException {
		Optional<Severity> onTheRule = Severity.parse(severity, toString());
		if (onTheRule.isPresent()) {
			return onTheRule.get();
		}
		return Severity.parse(RuleDefaults.severity(), RuleDefaults.SEVERITY_PROPERTY).orElse(Severity.DEFAULT);
	}

	/** The configured baseline, the one {@link RuleDefaults} derives, or null. */
	private File baselineFile() {
		return baselineFile != null ? baselineFile : RuleDefaults.baselineFile(ruleName()).orElse(null);
	}

	/** The configured report, the one {@link RuleDefaults} derives, or null. */
	private File reportFile() {
		return reportFile != null ? reportFile : RuleDefaults.reportFile(ruleName()).orElse(null);
	}

	/**
	 * The name the pom configures this rule under, so a report and a baseline derived
	 * from a directory are named what the build's configuration calls the rule. It is
	 * also how a composite labels the violations of each part it ran.
	 *
	 * <p>It is derived from the class name — the simple name without its {@code Rule}
	 * suffix, decapitalised — rather than read back from the {@code @Named}
	 * annotation that carries it. {@code ruleNamesFollowTheClassName} in the
	 * architecture tests holds every rule's annotation to this derivation, so a
	 * reader can predict the one string a pom, CLAUDE.md and AGENTS.md all name.
	 * Deriving it also keeps {@code javax.inject} off the path a rule takes to
	 * report: reading the annotation put it there, and the adopt module — which runs
	 * the real claudeMdFormat rule with no injection framework anywhere — failed on a
	 * class it never uses.
	 */
	public final String ruleName() {
		String simpleName = getClass().getSimpleName();
		String withoutSuffix = simpleName.endsWith(RULE_SUFFIX)
				? simpleName.substring(0, simpleName.length() - RULE_SUFFIX.length())
				: simpleName;
		return withoutSuffix.isEmpty() ? simpleName
				: withoutSuffix.substring(0, 1).toLowerCase(Locale.ROOT) + withoutSuffix.substring(1);
	}

	/**
	 * The logger, never null. maven-enforcer injects one before it runs a rule and
	 * nothing else does, so {@link SilentEnforcerLogger} stands in for a rule
	 * constructed directly — which is what lets a rule log on any path rather than
	 * only where a Maven session is guaranteed.
	 */
	protected final EnforcerLogger log() {
		return Objects.requireNonNullElse(getLog(), SilentEnforcerLogger.INSTANCE);
	}

	/**
	 * One line per rule naming what it was pointed at and what it found there.
	 * maven-enforcer reports each verdict by class and configured name alone, so
	 * nothing in {@code Rule 8: ...McpServersValidRule (mcpServersValid) passed}
	 * distinguishes the rule that read a file from the one that passed because its
	 * optional file is absent. The rule names its own inputs through
	 * {@link #toString()}, so {@code mvn -X} shows what each verdict was reached on.
	 */
	private void logOutcome(List<String> violations) {
		log().debug(() -> this + " found " + violations.size() + " violation(s)");
	}

	/**
	 * Says why a warning did not stop the build, and which rule to configure to make
	 * it. A downgraded violation is otherwise one more {@code [WARNING]} worded
	 * exactly like the failure it would have been.
	 */
	private String downgradeNotice() {
		return "  (" + this + " has severity '" + Severity.WARN.configuredName()
				+ "', so the build was not failed)";
	}

	/**
	 * Whether this run is recording a baseline rather than checking against one.
	 * Asking for one with nowhere to write it is refused rather than ignored: read as
	 * "no baseline, so check normally", it told the operator the build failed on the
	 * very violations they had just asked to accept. Like every build-setup mistake
	 * this fails whatever the severity.
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
	 * build collects them into one directory. A rule pointed at its own
	 * {@code reportFile} is not indexed: nothing says that file sits with anyone
	 * else's. A failure here is logged rather than thrown — the verdict is already
	 * decided, and a build must not go red because the index could not be refreshed.
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
	 * Fails when a required parameter was not configured, which is a build-setup
	 * mistake and so always fails regardless of {@link #severity}.
	 */
	protected final void requireConfigured(Object parameter, String name) throws EnforcerRuleException {
		if (parameter == null) {
			throw new EnforcerRuleException("The " + name + " parameter is not configured");
		}
	}

	/**
	 * Fails when a required input file does not exist. Like a missing parameter this
	 * always fails regardless of {@link #severity}; {@code description} names the
	 * file, e.g. {@code CLAUDE.md}.
	 */
	protected final void requireExists(File file, String description) throws EnforcerRuleException {
		if (!file.isFile()) {
			throw new EnforcerRuleException(description + " does not exist at " + file);
		}
	}

	/** The pair of build-setup checks every rule runs before reading a file. */
	protected final void requireDocument(File file, String name) throws EnforcerRuleException {
		requireConfigured(file, name);
		requireExists(file, name);
	}

	/**
	 * Reads a required input file, with any leading byte-order mark stripped, and
	 * fails when it cannot be decoded as UTF-8 — as a <em>rule verdict</em> naming
	 * the file. Reading through {@link MarkdownText#read} instead let an
	 * {@link java.io.UncheckedIOException} escape and abort the whole build as an
	 * internal error: a {@code .gitignore} carrying a single Latin-1 byte took the
	 * build down with a stack trace instead of the verdict the rule exists to give.
	 * The read goes through {@link DocumentCache}, so the four rules that each check
	 * a section of {@code settings.json} read it once between them.
	 */
	protected final String requireText(File file, String description) throws EnforcerRuleException {
		return DocumentCache.text(file, () -> MarkdownText.readIfText(file))
				.orElseThrow(() -> new EnforcerRuleException(
						description + " cannot be read as UTF-8 text: " + file));
	}

	/**
	 * Reads a required input file and fails when it is blank, always regardless of
	 * {@link #severity}. Returns the content so the caller can validate it further.
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
	 * class up, skipping the unset ones and the shared reporting parameters declared
	 * here. Maven identifies a rule in its log by this string, so what matters is
	 * which files the failing configuration pointed at — deriving that from the
	 * parameters saves every rule repeating the same override.
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
