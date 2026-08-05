package io.github.adamw7.tools.enforcer.rule;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

import org.apache.maven.enforcer.rule.api.EnforcerRuleException;

/**
 * Compiles the regular expressions a rule takes from its configuration. A pattern
 * that is not a valid regular expression is a build-setup mistake, so it fails
 * with a message naming the parameter it was written under rather than letting a
 * {@link PatternSyntaxException} escape as an internal build error. Every rule
 * that takes a pattern says so the same way, which is why the wording lives here
 * rather than in each of them.
 */
public final class Patterns {

	private Patterns() {
	}

	/**
	 * @param regex     the configured pattern
	 * @param parameter the configuration parameter it was written under, e.g.
	 *                  {@code secretPattern}, named in the failure message
	 */
	public static Pattern compile(String regex, String parameter) throws EnforcerRuleException {
		try {
			return Pattern.compile(regex);
		} catch (PatternSyntaxException e) {
			throw new EnforcerRuleException(
					parameter + " '" + regex + "' is not a valid regular expression: " + e.getDescription());
		}
	}

	/** Every configured pattern, compiled in order; a {@code null} list is read as none configured. */
	public static List<Pattern> compileAll(List<String> regexes, String parameter) throws EnforcerRuleException {
		List<Pattern> compiled = new ArrayList<>();
		for (String regex : regexes != null ? regexes : List.<String>of()) {
			compiled.add(compile(regex, parameter));
		}
		return compiled;
	}
}
