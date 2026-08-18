package io.github.adamw7.tools.enforcer.rule;

import java.io.File;
import java.util.Optional;

/**
 * The reporting configuration a build sets once for every rule, read from system
 * properties so a pom does not have to repeat it per rule.
 *
 * <p>A project wiring the full catalogue configures around twenty rules, and
 * {@code severity}, {@code reportFile} and {@code baselineFile} are the three
 * parameters that are the <em>same answer</em> for all of them: a team adopting
 * the rules gradually wants every rule downgraded, not one, and a build
 * publishing reports wants all of them under one directory. Spelled per rule that
 * is sixty elements to keep in step, and a rule left out of the sweep is a rule
 * whose severity silently differs from its neighbours'.
 *
 * <table>
 * <caption>The properties and what they default</caption>
 * <tr><th>Property</th><th>Defaults</th></tr>
 * <tr><td>{@code claude.enforcer.severity}</td><td>every rule's {@code severity}</td></tr>
 * <tr><td>{@code claude.enforcer.reportDir}</td><td>{@code reportFile} to {@code <dir>/<ruleName>.html}</td></tr>
 * <tr><td>{@code claude.enforcer.baselineDir}</td><td>{@code baselineFile} to {@code <dir>/<ruleName>.txt}</td></tr>
 * </table>
 *
 * <p>A parameter configured on the rule itself always wins: the property is a
 * default, not an override, so a build can downgrade the catalogue and still
 * insist on one rule. Naming the files after the rule is what makes the
 * directories work at all — two rules sharing one report file would each
 * overwrite the other's verdict, and one shared baseline would let a violation
 * accepted for one rule suppress an identical message from another.
 */
final class RuleDefaults {

	static final String SEVERITY_PROPERTY = "claude.enforcer.severity";
	static final String REPORT_DIR_PROPERTY = "claude.enforcer.reportDir";
	static final String BASELINE_DIR_PROPERTY = "claude.enforcer.baselineDir";

	private RuleDefaults() {
	}

	/** The severity every rule falls back to, as configured text for {@link Severity#parse}. */
	static String severity() {
		return property(SEVERITY_PROPERTY).orElse(null);
	}

	/** @return {@code <reportDir>/<ruleName>.html}, or empty when no report directory is set */
	static Optional<File> reportFile(String ruleName) {
		return property(REPORT_DIR_PROPERTY).map(directory -> new File(directory, ruleName + ".html"));
	}

	/** @return {@code <baselineDir>/<ruleName>.txt}, or empty when no baseline directory is set */
	static Optional<File> baselineFile(String ruleName) {
		return property(BASELINE_DIR_PROPERTY).map(directory -> new File(directory, ruleName + ".txt"));
	}

	/** @return the directory reports are collected under, for the index that links them */
	static Optional<File> reportDirectory() {
		return property(REPORT_DIR_PROPERTY).map(File::new);
	}

	/** A property set to whitespace names no value, the same reading a blank pom element gets. */
	private static Optional<String> property(String name) {
		return Optional.ofNullable(System.getProperty(name)).map(String::strip).filter(value -> !value.isEmpty());
	}
}
