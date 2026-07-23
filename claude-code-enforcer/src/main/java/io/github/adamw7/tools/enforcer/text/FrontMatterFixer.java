package io.github.adamw7.tools.enforcer.text;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Repairs the unambiguous ways a Claude Code front matter block is commonly
 * malformed, so an auto-fixing rule can rewrite the file rather than only fail
 * the build:
 * <ul>
 * <li>a delimiter written with too many dashes, such as {@code ----}, which
 * {@link FrontMatter} does not recognise as the canonical {@code ---};</li>
 * <li>an opening {@code ---} whose closing delimiter is missing, which leaves
 * the block unterminated; and</li>
 * <li>blank lines before the opening delimiter, which Claude Code does not
 * accept — the block must start on the first line.</li>
 * </ul>
 * <p>
 * The repair is deliberately conservative: it only acts when the document opens
 * (past any leading blank lines) with a dashes-only line and the region that
 * would become the block actually contains at least one {@code key: value}
 * entry, so a lone {@code ---} thematic break is never mistaken for front
 * matter. A document whose front matter already parses, or whose problem is not
 * one of the above, is left untouched and reported by the rule as before.
 */
public final class FrontMatterFixer {

	private static final char DASH = '-';
	private static final int MIN_DELIMITER_DASHES = 3;
	private static final String CANONICAL_DELIMITER = "---";
	private static final Pattern KEY_ENTRY = Pattern.compile("[A-Za-z0-9_][A-Za-z0-9_. -]*:(\\s.*)?");

	private FrontMatterFixer() {
	}

	/**
	 * Returns the repaired document when {@code content} has a malformed front
	 * matter block this fixer can safely correct, or empty when there is nothing
	 * to fix or the problem is outside its scope. The returned content always
	 * parses as front matter and never equals the input.
	 */
	public static Optional<String> repair(String content) {
		if (FrontMatter.parse(content).isPresent()) {
			return Optional.empty();
		}
		List<String> lines = new ArrayList<>(content.lines().toList());
		int open = firstNonBlankIndex(lines);
		if (open < 0 || !isDelimiterLike(lines.get(open))) {
			return Optional.empty();
		}
		return repairFrom(lines, open).map(fixed -> render(withoutLeadingBlanks(fixed), content));
	}

	/** The lines with any blank lines before the opening delimiter removed, so the block starts on line one. */
	private static List<String> withoutLeadingBlanks(List<String> lines) {
		int first = firstNonBlankIndex(lines);
		return first <= 0 ? lines : lines.subList(first, lines.size());
	}

	private static Optional<List<String>> repairFrom(List<String> lines, int open) {
		int close = nextDelimiterLike(lines, open + 1);
		if (close > 0) {
			return normalizeDelimiters(lines, open, close);
		}
		return insertClosingDelimiter(lines, open);
	}

	private static Optional<List<String>> normalizeDelimiters(List<String> lines, int open, int close) {
		if (!containsKeyEntry(lines, open + 1, close)) {
			return Optional.empty();
		}
		List<String> fixed = new ArrayList<>(lines);
		fixed.set(open, CANONICAL_DELIMITER);
		fixed.set(close, CANONICAL_DELIMITER);
		return Optional.of(fixed);
	}

	private static Optional<List<String>> insertClosingDelimiter(List<String> lines, int open) {
		int end = endOfBlock(lines, open + 1);
		if (!containsKeyEntry(lines, open + 1, end)) {
			return Optional.empty();
		}
		List<String> fixed = new ArrayList<>(lines);
		fixed.set(open, CANONICAL_DELIMITER);
		fixed.add(end, CANONICAL_DELIMITER);
		return Optional.of(fixed);
	}

	/** The first index at or after {@code from} that is no longer a front-matter entry. */
	private static int endOfBlock(List<String> lines, int from) {
		int index = from;
		while (index < lines.size() && isFrontMatterLine(lines.get(index))) {
			index++;
		}
		return index;
	}

	private static boolean containsKeyEntry(List<String> lines, int from, int toExclusive) {
		for (int i = from; i < toExclusive; i++) {
			if (KEY_ENTRY.matcher(lines.get(i).strip()).matches()) {
				return true;
			}
		}
		return false;
	}

	private static boolean isFrontMatterLine(String line) {
		String stripped = line.strip();
		if (stripped.isEmpty() || line.charAt(0) == ' ' || line.charAt(0) == '\t' || stripped.startsWith("-")) {
			return !isDelimiterLike(line);
		}
		return KEY_ENTRY.matcher(stripped).matches();
	}

	private static int firstNonBlankIndex(List<String> lines) {
		for (int i = 0; i < lines.size(); i++) {
			if (!lines.get(i).isBlank()) {
				return i;
			}
		}
		return -1;
	}

	private static int nextDelimiterLike(List<String> lines, int from) {
		for (int i = from; i < lines.size(); i++) {
			if (isDelimiterLike(lines.get(i))) {
				return i;
			}
		}
		return -1;
	}

	private static boolean isDelimiterLike(String line) {
		String stripped = line.strip();
		return stripped.length() >= MIN_DELIMITER_DASHES && allDashes(stripped);
	}

	private static boolean allDashes(String value) {
		return value.chars().allMatch(character -> character == DASH);
	}

	private static String render(List<String> lines, String original) {
		String joined = String.join("\n", lines);
		return endsWithNewline(original) ? joined + "\n" : joined;
	}

	private static boolean endsWithNewline(String content) {
		return content.endsWith("\n") || content.endsWith("\r");
	}
}
