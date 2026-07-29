package io.github.adamw7.tools.enforcer.text;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * A parsed Markdown document: its lines plus a mask marking which lines belong to
 * a fenced code block. Parsing the mask once, at construction, lets the structural
 * checks share it instead of each rebuilding it.
 * <p>
 * Headings are recognised on whole lines outside fenced code blocks, so a heading
 * mentioned inside a fence or in prose is not treated as document structure. The
 * mask includes the opening and closing delimiters themselves, so heading and body
 * detection agree on what is code. A heading is an ATX one — one to six {@code #}
 * characters followed by whitespace or nothing else — so a line that merely starts
 * with a hash, such as {@code #1 rule: run mvn install}, stays prose. Counting it
 * as a heading would end the section it sits in and report that section as empty.
 * <p>
 * A fence is closed only by a run of the <em>same</em> character, at least as long
 * as the one that opened it, carrying no info string. All three conditions matter,
 * because documentation about Markdown nests one fence inside another: a
 * {@code ~~~} line inside a {@code ```} block, the {@code ```java} line of an
 * example inside a {@code ````} wrapper, and a {@code ```} example inside a
 * {@code ````} wrapper all stay content. Closing on the first character alone would
 * end the block early and then treat the real closing delimiter as a fresh opening
 * one, silently masking the rest of the document as code.
 */
public final class MarkdownDocument {

	private static final char BACKTICK = '`';
	private static final char TILDE = '~';
	private static final int MIN_FENCE_LENGTH = 3;
	private static final char HEADING_CHAR = '#';

	/** One to six {@code #} characters, then whitespace and any text, or nothing at all. */
	private static final Pattern ATX_HEADING = Pattern.compile("#{1,6}(\\s.*)?");

	private final List<String> lines;
	private final boolean[] insideFence;

	private MarkdownDocument(List<String> lines, boolean[] insideFence) {
		this.lines = lines;
		this.insideFence = insideFence;
	}

	/** Parses {@code content} into lines and a fenced-code-block mask. */
	public static MarkdownDocument parse(String content) {
		List<String> lines = content.lines().toList();
		return new MarkdownDocument(lines, fenceMask(lines));
	}

	public int lineCount() {
		return lines.size();
	}

	/** The raw line at {@code index}, without trimming. */
	public String line(int index) {
		return lines.get(index);
	}

	/** True when the line at {@code index} is part of a fenced code block. */
	public boolean isInsideFence(int index) {
		return insideFence[index];
	}

	/** The first line that is not blank, stripped of surrounding whitespace, or empty if none. */
	public String firstNonBlankLine() {
		return MarkdownText.firstNonBlankLine(lines.stream());
	}

	/** True when {@code token} appears on a line outside a fenced code block. */
	public boolean containsOutsideFences(String token) {
		for (int i = 0; i < lines.size(); i++) {
			if (!insideFence[i] && lines.get(i).contains(token)) {
				return true;
			}
		}
		return false;
	}

	/** The heading lines outside fenced code blocks, in document order. */
	public Set<String> headings() {
		Set<String> headings = new LinkedHashSet<>();
		for (int i = 0; i < lines.size(); i++) {
			String trimmed = lines.get(i).strip();
			if (!insideFence[i] && isHeading(trimmed)) {
				headings.add(trimmed);
			}
		}
		return headings;
	}

	/** True when {@code heading} appears as a real heading outside code fences. */
	public boolean hasHeading(String heading) {
		return headings().contains(heading);
	}

	/**
	 * True when {@code heading} is present and is followed, before the next heading
	 * at its own level or shallower, by any prose, a code block, or a deeper
	 * sub-heading. A deeper heading is content that belongs to the section; a
	 * sibling or parent heading ends it.
	 */
	public boolean hasBody(String heading) {
		int index = headingIndex(heading);
		return index >= 0 && hasBodyAt(index);
	}

	/**
	 * The headings from {@code wanted} that are present, in the order they first
	 * appear in the document, each listed once. A required heading that appears
	 * more than once is reported by its first occurrence, so the order comparison
	 * matches the de-duplicated set of present sections rather than reporting a
	 * spurious out-of-order failure.
	 */
	public List<String> headingsInOrder(List<String> wanted) {
		Set<String> required = new LinkedHashSet<>(wanted);
		List<String> ordered = new ArrayList<>();
		for (int i = 0; i < lines.size(); i++) {
			if (!insideFence[i] && required.remove(lines.get(i).strip())) {
				ordered.add(lines.get(i).strip());
			}
		}
		return ordered;
	}

	private int headingIndex(String section) {
		for (int i = 0; i < lines.size(); i++) {
			if (!insideFence[i] && lines.get(i).strip().equals(section)) {
				return i;
			}
		}
		return -1;
	}

	private boolean hasBodyAt(int headingIndex) {
		int sectionLevel = headingLevel(lines.get(headingIndex).strip());
		for (int i = headingIndex + 1; i < lines.size(); i++) {
			String line = lines.get(i).strip();
			if (insideFence[i]) {
				return true;
			}
			if (isHeading(line)) {
				return headingLevel(line) > sectionLevel;
			}
			if (!line.isEmpty()) {
				return true;
			}
		}
		return false;
	}

	private static boolean isHeading(String line) {
		return ATX_HEADING.matcher(line).matches();
	}

	private static int headingLevel(String heading) {
		int level = 0;
		while (level < heading.length() && heading.charAt(level) == HEADING_CHAR) {
			level++;
		}
		return level;
	}

	private static boolean[] fenceMask(List<String> lines) {
		boolean[] mask = new boolean[lines.size()];
		Delimiter open = null;
		for (int i = 0; i < lines.size(); i++) {
			open = applyFence(lines.get(i).strip(), open, mask, i);
		}
		return mask;
	}

	/**
	 * Marks line {@code index} as code or structure and returns the fence delimiter
	 * still open afterwards.
	 */
	private static Delimiter applyFence(String line, Delimiter open, boolean[] mask, int index) {
		Delimiter delimiter = delimiterOf(line);
		if (open == null) {
			mask[index] = delimiter != null;
			return delimiter;
		}
		mask[index] = true;
		return delimiter != null && delimiter.closes(open) ? null : open;
	}

	/** The fence delimiter a line declares, or null when the line is not one. */
	private static Delimiter delimiterOf(String line) {
		if (line.isEmpty() || !isFenceCharacter(line.charAt(0))) {
			return null;
		}
		char character = line.charAt(0);
		int length = runLength(line, character);
		if (length < MIN_FENCE_LENGTH) {
			return null;
		}
		return new Delimiter(character, length, !line.substring(length).isBlank());
	}

	private static boolean isFenceCharacter(char character) {
		return character == BACKTICK || character == TILDE;
	}

	private static int runLength(String line, char character) {
		int length = 0;
		while (length < line.length() && line.charAt(length) == character) {
			length++;
		}
		return length;
	}

	/**
	 * One fence delimiter line: the character it is written with, how many of them
	 * it runs, and whether anything follows them — the info string of an opening
	 * delimiter, such as the {@code java} of {@code ```java}.
	 */
	private record Delimiter(char character, int length, boolean info) {

		/**
		 * True when this line closes the block {@code open} started. A closing
		 * delimiter uses the same character, is at least as long, and carries no
		 * info string, so a shorter or annotated fence nested inside the block is
		 * content rather than its end.
		 */
		boolean closes(Delimiter open) {
			return character == open.character() && length >= open.length() && !info;
		}
	}
}
