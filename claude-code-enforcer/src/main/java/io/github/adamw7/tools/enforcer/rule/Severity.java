package io.github.adamw7.tools.enforcer.rule;

import java.util.Arrays;
import java.util.Locale;
import java.util.Optional;
import java.util.stream.Collectors;

import org.apache.maven.enforcer.rule.api.EnforcerRuleException;

/**
 * What a rule does with the violations it collected: fail the build, or log them
 * and let it through. Configured as {@code <severity>} on any rule, defaulting to
 * {@link #ERROR}.
 *
 * <p>An unrecognised value is refused rather than read as the default. Deciding
 * "not {@code warn}, therefore {@code error}" meant {@code <severity>warning</severity>}
 * failed the build while its author believed the rule had been downgraded — the
 * one misconfiguration whose symptom is indistinguishable from the rule working.
 * Like every other build-setup mistake, it fails immediately instead.
 */
public enum Severity {

	/** Collected violations fail the build. The default. */
	ERROR,

	/** Collected violations are logged and the build continues. */
	WARN;

	/** The default when neither the rule nor {@link RuleDefaults} names one. */
	static final Severity DEFAULT = ERROR;

	/**
	 * Reads a configured severity, tolerating case and surrounding whitespace, since
	 * a {@code <severity>} element indented across lines arrives padded.
	 *
	 * @param configured the value as configured, which may be null or blank for none
	 * @return the severity it names, or empty when it names none at all
	 * @throws EnforcerRuleException when it names something that is not a severity
	 */
	static Optional<Severity> parse(String configured, String ruleLabel) throws EnforcerRuleException {
		if (configured == null || configured.isBlank()) {
			return Optional.empty();
		}
		String normalized = configured.strip().toUpperCase(Locale.ROOT);
		return Optional.of(Arrays.stream(values())
				.filter(severity -> severity.name().equals(normalized))
				.findFirst()
				.orElseThrow(() -> unrecognized(configured, ruleLabel)));
	}

	private static EnforcerRuleException unrecognized(String configured, String ruleLabel) {
		return new EnforcerRuleException("The severity '" + configured.strip() + "' configured on " + ruleLabel
				+ " is not a severity. Use one of " + names() + ". A value that is neither was previously read"
				+ " as '" + DEFAULT.configuredName() + "', so a rule meant to be downgraded still failed the build.");
	}

	private static String names() {
		return Arrays.stream(values()).map(Severity::configuredName).collect(Collectors.joining(", "));
	}

	/** The lower-case spelling a pom configures, which is how every message names one. */
	String configuredName() {
		return name().toLowerCase(Locale.ROOT);
	}
}
