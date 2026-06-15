package io.github.adamw7.tools.enforcer.text;

import java.util.List;
import java.util.regex.Pattern;

/**
 * The shared rules for a Claude Code {@code name} front matter value. A name
 * must be lower-case kebab-case, no longer than the allowed maximum, and equal
 * to the identifier its file or directory already uses, so the catalogue cannot
 * drift from what is on disk. Skills and sub-agents both follow this convention,
 * so the checks live in one place.
 */
public final class NameConvention {

	/** Lower-case alphanumerics in hyphen-separated words, e.g. {@code git-commit}. */
	private static final Pattern KEBAB_CASE = Pattern.compile("[a-z0-9]+(-[a-z0-9]+)*");

	/** The Claude Code limit for skill and sub-agent names. */
	public static final int MAX_LENGTH = 64;

	private NameConvention() {
	}

	/**
	 * Adds a violation for each way {@code name} breaks convention for the file
	 * described by {@code where}, expecting it to equal {@code expected} (the
	 * directory or file identifier). An empty name is reported once and the other
	 * checks are skipped, since they would only restate the same problem.
	 */
	public static void collect(String name, String expected, String where, List<String> violations) {
		if (name.isBlank()) {
			violations.add("name must not be empty in " + where);
			return;
		}
		if (name.length() > MAX_LENGTH) {
			violations.add("name '" + name + "' exceeds " + MAX_LENGTH + " characters in " + where);
		}
		if (!KEBAB_CASE.matcher(name).matches()) {
			violations.add("name '" + name + "' must be lower-case kebab-case in " + where);
		}
		if (!name.equals(expected)) {
			violations.add("name '" + name + "' must match '" + expected + "' in " + where);
		}
	}
}
