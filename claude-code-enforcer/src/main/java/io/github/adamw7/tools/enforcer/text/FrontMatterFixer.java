package io.github.adamw7.tools.enforcer.text;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;
import java.util.regex.Pattern;
import java.util.stream.IntStream;

/**
 * Repairs the unambiguous ways a Claude Code front matter block is commonly
 * malformed, so an auto-fixing rule can rewrite the file rather than only fail the
 * build:
 * <ul>
 * <li>a delimiter written with too many dashes, such as {@code ----}, which
 * {@link FrontMatter} does not recognise as the canonical {@code ---};</li>
 * <li>an opening {@code ---} whose closing delimiter is missing; and</li>
 * <li>blank lines before the opening delimiter, which Claude Code does not
 * accept — the block must start on the first line.</li>
 * </ul>
 * <p>
 * The repair is deliberately conservative: it only acts when the document opens
 * (past any leading blank lines) with a dashes-only line and the region that would
 * become the block actually contains at least one {@code key: value} entry, so a
 * lone {@code ---} thematic break is never mistaken for front matter. A document
 * whose front matter already parses, or whose problem is not one of the above, is
 * left untouched, and one that is repaired keeps the line separator it was written
 * with, so repairing two delimiter lines does not rewrite every other line of a
 * CRLF file.
 * <p>
 * A repair that changes nothing is no repair, and is reported as none. Not every
 * block {@link FrontMatter} declines to read is one this fixer can mend — a block
 * whose delimiters are already canonical and whose <em>YAML</em> is malformed is
 * beyond it — and returning that document unchanged would have the caller write the
 * file it already had, and log a fix, on every build.
 */
public final class FrontMatterFixer {

	private static final char DASH = '-';
	private static final int MIN_DELIMITER_DASHES = 3;
	private static final String CANONICAL_DELIMITER = "---";
	private static final String CARRIAGE_RETURN = "\r";
	private static final String LINE_FEED = "\n";
	/**
	 * A {@code key:} or {@code key: value} entry. The key is a single identifier —
	 * {@code name}, {@code description}, {@code allowed-tools} — with no spaces in
	 * it, which is the shape every Claude Code front matter key takes. Allowing a
	 * space would make ordinary prose such as {@code Some notes: here.} look like an
	 * entry, and a document opening with a thematic break followed by that prose
	 * would then be repaired into front matter it never had.
	 */
	private static final Pattern KEY_ENTRY = Pattern.compile("[A-Za-z0-9_][A-Za-z0-9_.-]*:(\\s.*)?");

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
		return repairDelimiters(lines, open).map(fixed -> render(withoutLeadingBlanks(fixed), content))
				.filter(repaired -> !repaired.equals(content));
	}

	/** The lines with any blank lines before the opening delimiter removed, so the block starts on line one. */
	private static List<String> withoutLeadingBlanks(List<String> lines) {
		int first = firstNonBlankIndex(lines);
		return first <= 0 ? lines : lines.subList(first, lines.size());
	}

	/**
	 * The lines with both delimiters written canonically, adding the closing one
	 * when the block never closed. Empty when the region the block would cover
	 * carries no {@code key: value} entry, so a lone thematic break is left alone.
	 */
	private static Optional<List<String>> repairDelimiters(List<String> lines, int open) {
		int close = nextDelimiterLike(lines, open + 1);
		int end = close > 0 ? close : endOfBlock(lines, open + 1);
		if (!containsKeyEntry(lines, open + 1, end)) {
			return Optional.empty();
		}
		List<String> fixed = new ArrayList<>(lines);
		fixed.set(open, CANONICAL_DELIMITER);
		if (close > 0) {
			fixed.set(close, CANONICAL_DELIMITER);
		} else {
			fixed.add(end, CANONICAL_DELIMITER);
		}
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
		return lines.subList(from, toExclusive).stream()
				.anyMatch(line -> KEY_ENTRY.matcher(line.strip()).matches());
	}

	private static boolean isFrontMatterLine(String line) {
		String stripped = line.strip();
		if (stripped.isEmpty() || line.charAt(0) == ' ' || line.charAt(0) == '\t' || stripped.startsWith("-")) {
			return !isDelimiterLike(line);
		}
		return KEY_ENTRY.matcher(stripped).matches();
	}

	private static int firstNonBlankIndex(List<String> lines) {
		return indexOf(lines, 0, line -> !line.isBlank());
	}

	private static int nextDelimiterLike(List<String> lines, int from) {
		return indexOf(lines, from, FrontMatterFixer::isDelimiterLike);
	}

	/** The first index at or after {@code from} whose line {@code matches}, or -1 when none does. */
	private static int indexOf(List<String> lines, int from, Predicate<String> matches) {
		return IntStream.range(from, lines.size()).filter(index -> matches.test(lines.get(index)))
				.findFirst().orElse(-1);
	}

	private static boolean isDelimiterLike(String line) {
		String stripped = line.strip();
		return stripped.length() >= MIN_DELIMITER_DASHES && allDashes(stripped);
	}

	private static boolean allDashes(String value) {
		return value.chars().allMatch(character -> character == DASH);
	}

	private static String render(List<String> lines, String original) {
		String separator = separatorOf(original);
		String joined = String.join(separator, lines);
		return endsWithNewline(original) ? joined + separator : joined;
	}

	/**
	 * The line separator the document is already written with. Rebuilding it with
	 * {@code \n} regardless rewrote every line of a CRLF file to repair its two
	 * delimiter lines, turning a two-line fix into a whole-file diff on Windows.
	 */
	private static String separatorOf(String content) {
		int carriageReturn = content.indexOf(CARRIAGE_RETURN);
		if (carriageReturn < 0) {
			return LINE_FEED;
		}
		return content.startsWith(LINE_FEED, carriageReturn + 1) ? CARRIAGE_RETURN + LINE_FEED : CARRIAGE_RETURN;
	}

	private static boolean endsWithNewline(String content) {
		return content.endsWith(LINE_FEED) || content.endsWith(CARRIAGE_RETURN);
	}
}
