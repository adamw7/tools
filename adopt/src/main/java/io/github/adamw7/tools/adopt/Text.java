package io.github.adamw7.tools.adopt;

/**
 * The text checks the adoption's inputs share: rejecting a blank value that must
 * carry one, and reading an optional value that is absent when blank. Both entry
 * points — the command line and the MCP tool — treat a blank argument as "not
 * supplied" rather than as an invalid value, so keeping the rule here stops the
 * two from drifting apart on what counts as absent.
 */
public final class Text {

	private Text() {
	}

	/**
	 * @return the value stripped of surrounding whitespace
	 * @throws IllegalArgumentException when it is {@code null} or blank
	 */
	public static String required(String value, String field) {
		if (value == null || value.isBlank()) {
			throw new IllegalArgumentException(field + " must not be blank");
		}
		return value.strip();
	}

	/** @return whether the value carries text, so a blank one counts as not supplied */
	public static boolean isPresent(String value) {
		return value != null && !value.isBlank();
	}
}
