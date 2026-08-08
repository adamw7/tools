package io.github.adamw7.tools.test;

/**
 * Assertions about text that a test makes but {@code java.lang.String} does not
 * answer directly. A test proving that something is said <em>once</em> — a rule
 * reporting a file it was handed twice, an installer not duplicating a plugin
 * declaration — has to count occurrences, which every such test grew its own
 * copy of: one written as a hand-rolled {@code indexOf} loop, another as a split
 * on a quoted pattern.
 * <p>
 * Saying it here once keeps those tests to what they are actually asserting, and
 * settles the question the two implementations answered only by accident: what
 * happens when a token overlaps itself.
 */
public final class TestStrings {

	private TestStrings() {
	}

	/**
	 * Counts how many times {@code token} appears in {@code text}, without
	 * overlapping: each match resumes the search past the one just found, so
	 * {@code "aaa"} contains {@code "aa"} once rather than twice. {@code token} is
	 * matched literally, never as a regular expression.
	 *
	 * @param text  the text to search, which a failure message often is
	 * @param token the literal to count; an empty token counts as absent
	 * @return the number of non-overlapping occurrences
	 */
	public static int occurrences(String text, String token) {
		if (text == null || token == null || token.isEmpty()) {
			return 0;
		}
		int count = 0;
		int index = text.indexOf(token);
		while (index >= 0) {
			count++;
			index = text.indexOf(token, index + token.length());
		}
		return count;
	}
}
