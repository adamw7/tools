package io.github.adamw7.tools.enforcer.text;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * A parsed Markdown document: its lines plus the masks marking which of them belong
 * to a fenced code block and which sit inside an HTML comment. Parsing the masks
 * once, at construction, lets the structural checks share them instead of each
 * rebuilding them.
 * <p>
 * Headings are recognised on whole lines outside fenced code blocks, so a heading
 * mentioned inside a fence or in prose is not treated as document structure. The
 * mask includes the opening and closing delimiters themselves, so heading and body
 * detection agree on what is code. A heading is an ATX one — one to six {@code #}
 * characters followed by whitespace or nothing else — so a line that merely starts
 * with a hash, such as {@code #1 rule: run mvn install}, stays prose. Counting it
 * as a heading would end the section it sits in and report that section as empty.
 * <p>
 * A heading is also recognised only outside an HTML comment block. A section
 * commented out with {@code <!-- ... -->} is inert text, not structure, so counting
 * it would let a document satisfy a required-section check with the very heading its
 * author had removed.
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
	private static final String COMMENT_START = "<!--";
	private static final String COMMENT_END = "-->";

	/** One to six {@code #} characters, then whitespace and any text, or nothing at all. */
	private static final Pattern ATX_HEADING = Pattern.compile("#{1,6}(\\s.*)?");

	private final List<String> lines;
	private final boolean[] insideFence;
	private final boolean[] insideComment;

	private MarkdownDocument(List<String> lines, boolean[] insideFence, boolean[] insideComment) {
		this.lines = lines;
		this.insideFence = insideFence;
		this.insideComment = insideComment;
	}

	/** Parses {@code content} into lines, a fenced-code-block mask, and an HTML-comment mask. */
	public static MarkdownDocument parse(String content) {
		List<String> lines = content.lines().toList();
		boolean[] insideFence = fenceMask(lines);
		return new MarkdownDocument(lines, insideFence, commentMask(lines, insideFence));
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

	/**
	 * True when the line at {@code index} says something about the document rather
	 * than illustrating or retracting it: outside a fenced code block and outside an
	 * HTML comment alike. A rule that reads a line for what it declares — a token it
	 * forbids, a link it must resolve, a memory import it must follow — reads only
	 * these, for the same reason {@link #headings()} does: sample text inside a fence
	 * and text an author commented out are both inert.
	 */
	public boolean carriesStructure(int index) {
		return !insideFence[index] && !insideComment[index];
	}

	/** The first line that is not blank, stripped of surrounding whitespace, or empty if none. */
	public String firstNonBlankLine() {
		return MarkdownText.firstNonBlankLine(lines.stream());
	}

	/**
	 * The indices of the lines that can carry document structure: outside fenced code
	 * blocks and outside HTML comments alike. A heading is only recognised on one of
	 * these.
	 */
	private IntStream structuralLines() {
		return IntStream.range(0, lines.size()).filter(this::carriesStructure);
	}

	/**
	 * True when {@code token} appears on a line that carries document structure. A
	 * mention inside a fenced code block is an example and one inside an HTML comment
	 * is text its author removed, so neither states anything the document still says:
	 * counting them let a commented-out reference satisfy the check that demands it,
	 * and reported a commented-out token as forbidden content.
	 */
	public boolean containsOnStructuralLine(String token) {
		return structuralLines().anyMatch(index -> lines.get(index).contains(token));
	}

	/** The heading lines outside fenced code blocks and HTML comments, in document order. */
	public Set<String> headings() {
		return structuralLines()
				.mapToObj(index -> lines.get(index).strip())
				.filter(MarkdownDocument::isHeading)
				.collect(Collectors.toCollection(LinkedHashSet::new));
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
		structuralLines().mapToObj(index -> lines.get(index).strip())
				.filter(required::remove)
				.forEach(ordered::add);
		return ordered;
	}

	private int headingIndex(String section) {
		return structuralLines().filter(index -> lines.get(index).strip().equals(section)).findFirst().orElse(-1);
	}

	private boolean hasBodyAt(int headingIndex) {
		int sectionLevel = headingLevel(lines.get(headingIndex).strip());
		for (int i = headingIndex + 1; i < lines.size(); i++) {
			Optional<Boolean> verdict = bodyVerdict(i, sectionLevel);
			if (verdict.isPresent()) {
				return verdict.get();
			}
		}
		return false;
	}

	/**
	 * Whether the line at {@code index} settles the question of the section's body,
	 * or empty when it says nothing either way. A blank line does not, and neither
	 * does a commented-out one: a section whose only content is inside an HTML
	 * comment reads as empty, which is what commenting it out meant.
	 */
	private Optional<Boolean> bodyVerdict(int index, int sectionLevel) {
		if (insideComment[index]) {
			return Optional.empty();
		}
		if (insideFence[index]) {
			return Optional.of(Boolean.TRUE);
		}
		String line = lines.get(index).strip();
		if (isHeading(line)) {
			return Optional.of(headingLevel(line) > sectionLevel);
		}
		return line.isEmpty() ? Optional.empty() : Optional.of(Boolean.TRUE);
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
	 * Marks the lines an HTML comment spans, so a commented-out section is not read
	 * as document structure. A comment that opens and closes on one line masks
	 * nothing — {@code <!-- ## Testing -->} is not a heading to begin with — while a
	 * block comment hid a required section from the check that exists to demand it
	 * and satisfied it at the same time. A comment inside a fenced code block is
	 * sample text, so the fence wins.
	 */
	private static boolean[] commentMask(List<String> lines, boolean[] insideFence) {
		boolean[] mask = new boolean[lines.size()];
		boolean open = false;
		for (int i = 0; i < lines.size(); i++) {
			open = applyComment(lines.get(i).strip(), open, insideFence[i], mask, i);
		}
		return mask;
	}

	/**
	 * Marks line {@code index} as commented or not and returns whether a comment is
	 * still open afterwards.
	 */
	private static boolean applyComment(String line, boolean open, boolean insideFence, boolean[] mask, int index) {
		if (insideFence) {
			return open;
		}
		if (open) {
			mask[index] = true;
			return !line.contains(COMMENT_END);
		}
		mask[index] = line.startsWith(COMMENT_START) && !line.contains(COMMENT_END);
		return mask[index];
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
