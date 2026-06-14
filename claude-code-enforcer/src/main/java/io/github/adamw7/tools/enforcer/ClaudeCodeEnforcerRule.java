package io.github.adamw7.tools.enforcer;

import java.util.List;

import org.apache.maven.enforcer.rule.api.AbstractEnforcerRule;
import org.apache.maven.enforcer.rule.api.EnforcerRuleException;

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
 */
abstract class ClaudeCodeEnforcerRule extends AbstractEnforcerRule {

	private static final String WARN = "warn";

	/** Optional override: {@code error} (default) fails the build, {@code warn} only logs. */
	private String severity;

	/**
	 * Reports the violations as a single grouped message. Throws when severity is
	 * {@code error} and there is at least one violation; logs a warning when
	 * severity is {@code warn}; does nothing when there are no violations.
	 */
	protected final void report(String header, List<String> violations) throws EnforcerRuleException {
		if (violations.isEmpty()) {
			return;
		}
		String message = format(header, violations);
		if (isWarn()) {
			getLog().warn(message);
		} else {
			throw new EnforcerRuleException(message);
		}
	}

	private String format(String header, List<String> violations) {
		String separator = System.lineSeparator() + "  - ";
		return header + separator + String.join(separator, violations);
	}

	private boolean isWarn() {
		return WARN.equalsIgnoreCase(severity);
	}

	void setSeverity(String severity) {
		this.severity = severity;
	}
}
