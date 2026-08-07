package io.github.adamw7.tools.enforcer.text;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * A parsed Markdown document: its lines plus the masks marking which of them are
 * code and which sit inside an HTML comment. Parsing the masks once, at
 * construction, lets the structural checks share them instead of each rebuilding
 * them.
 * <p>
 * Headings are recognised on whole lines outside code, so a heading mentioned in a
 * sample or in prose is not treated as document structure. The mask includes a
 * fence's opening and closing delimiters themselves, so heading and body detection
 * agree on what is code. A heading is an ATX one — one to six {@code #}
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
 * <p>
 * Code is also quoted the other way Markdown allows — by indenting it four columns,
 * a tab counting on to the next four-column stop — and those lines are masked
 * alike. Such a block opens only where one may, after a blank line, because an
 * indented line below a paragraph is a lazy continuation of that paragraph rather
 * than code; it then runs on through indented and blank lines until the next line
 * that is neither. Reading an indented sample as prose let a {@code ## Testing}
 * shown as an example satisfy the very check that demands that section, and
 * reported the sample's own {@code TODO} and its {@code [link](sample.md)} as the
 * document's own — while the identical text inside a fence was correctly ignored.
 */
public final class MarkdownDocument {

	private static final char BACKTICK = '`';
	private static final char TILDE = '~';
	private static final int MIN_FENCE_LENGTH = 3;

	private static final char TAB = '\t';
	private static final char SPACE = ' ';

	/** The indent Markdown reads as a code block, and the width a tab advances to. */
	private static final int CODE_INDENT = 4;

	private static final char HEADING_CHAR = '#';
	private static final String COMMENT_START = "<!--";
	private static final String COMMENT_END = "-->";

	/** One to six {@code #} characters, then whitespace and any text, or nothing at all. */
	private static final Pattern ATX_HEADING = Pattern.compile("#{1,6}(\\s.*)?");

	private final List<String> lines;
	private final boolean[] insideCode;
	private final boolean[] insideComment;

	private MarkdownDocument(List<String> lines, boolean[] insideCode, boolean[] insideComment) {
		this.lines = lines;
		this.insideCode = insideCode;
		this.insideComment = insideComment;
	}

	/** Parses {@code content} into lines, a code mask, and an HTML-comment mask. */
	public static MarkdownDocument parse(String content) {
		List<String> lines = content.lines().toList();
		boolean[] insideCode = codeMask(lines);
		return new MarkdownDocument(lines, insideCode, commentMask(lines, insideCode));
	}

	public int lineCount() {
		return lines.size();
	}

	/** The raw line at {@code index}, without trimming. */
	public String line(int index) {
		return lines.get(index);
	}

	/** True when the line at {@code index} is code, quoted by a fence or by its indent. */
	public boolean isInsideCode(int index) {
		return insideCode[index];
	}

	/**
	 * True when the line at {@code index} sits inside an HTML comment. A caller that
	 * reads a line for what it says — a link to resolve, an import to follow — asks
	 * this as well as {@link #isInsideCode}, because commented-out text is inert:
	 * following it reports a violation about content the author had already removed.
	 */
	public boolean isInsideComment(int index) {
		return insideComment[index];
	}

	/** The first line that is not blank, stripped of surrounding whitespace, or empty if none. */
	public String firstNonBlankLine() {
		return MarkdownText.firstNonBlankLine(lines.stream());
	}

	/** The indices of the lines outside code, in document order. */
	private IntStream outsideCode() {
		return IntStream.range(0, lines.size()).filter(index -> !insideCode[index]);
	}

	/**
	 * The indices of the lines that can carry document structure: outside code and
	 * outside HTML comments alike. A heading is only recognised on one of these.
	 */
	private IntStream structuralLines() {
		return outsideCode().filter(index -> !insideComment[index]);
	}

	/**
	 * True when {@code token} appears on a line that carries document structure:
	 * outside code and outside HTML comments alike. A commented-out mention is inert
	 * both ways round — it must not satisfy a check that demands the token, and must
	 * not trip one that forbids it.
	 */
	public boolean containsInProse(String token) {
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

	/** The first line that settles the question decides it; a section nothing settles has no body. */
	private boolean hasBodyAt(int headingIndex) {
		int sectionLevel = headingLevel(lines.get(headingIndex).strip());
		return IntStream.range(headingIndex + 1, lines.size())
				.filter(this::decidesBody)
				.limit(1)
				.anyMatch(index -> insideCode[index] || isBody(lines.get(index).strip(), sectionLevel));
	}

	/**
	 * Whether the line at {@code index} settles the question of the section's body.
	 * A blank line does not, and neither does a commented-out one: a section whose
	 * only content is inside an HTML comment reads as empty, which is what commenting
	 * it out meant.
	 */
	private boolean decidesBody(int index) {
		return insideCode[index] || (!insideComment[index] && !lines.get(index).isBlank());
	}

	/** A deeper sub-heading continues the section; one at its level or shallower ends it. */
	private static boolean isBody(String line, int sectionLevel) {
		return !isHeading(line) || headingLevel(line) > sectionLevel;
	}

	private static boolean isHeading(String line) {
		return ATX_HEADING.matcher(line).matches();
	}

	private static int headingLevel(String heading) {
		return runLength(heading, HEADING_CHAR);
	}

	/**
	 * Marks every line Markdown reads as code, both ways it is quoted: the fenced
	 * blocks first, then the indented ones over the same mask, so a fence's own
	 * lines are already spoken for and an indent inside one is content rather than a
	 * block of its own.
	 */
	private static boolean[] codeMask(List<String> lines) {
		boolean[] mask = fenceMask(lines);
		boolean mayOpen = true;
		for (int i = 0; i < lines.size(); i++) {
			mayOpen = applyIndent(lines.get(i), mayOpen, mask, i);
		}
		return mask;
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
	 * Marks line {@code index} as indented code or not and returns whether an
	 * indented line below it would open a block. Only a blank line, a line already
	 * masked as code, and the start of the document leave that open: an indented
	 * line below a paragraph is a lazy continuation of it, which is what stops an
	 * indented code block interrupting one. A blank line is left unmarked, since it
	 * carries nothing to read either way.
	 */
	private static boolean applyIndent(String line, boolean mayOpen, boolean[] mask, int index) {
		if (mask[index] || line.isBlank()) {
			return true;
		}
		mask[index] = mayOpen && indentWidth(line) >= CODE_INDENT;
		return mask[index];
	}

	/** The line's indent in columns, a tab counting on to the next four-column stop. */
	private static int indentWidth(String line) {
		int width = 0;
		int index = 0;
		while (index < line.length() && isIndent(line.charAt(index))) {
			width += line.charAt(index) == TAB ? CODE_INDENT - width % CODE_INDENT : 1;
			index++;
		}
		return width;
	}

	private static boolean isIndent(char character) {
		return character == SPACE || character == TAB;
	}

	/**
	 * Marks the lines an HTML comment spans, so a commented-out section is not read
	 * as document structure. A comment that opens and closes on one line masks
	 * nothing — {@code <!-- ## Testing -->} is not a heading to begin with — while a
	 * block comment hid a required section from the check that exists to demand it
	 * and satisfied it at the same time. A comment inside code is sample text, so
	 * the code wins.
	 */
	private static boolean[] commentMask(List<String> lines, boolean[] insideCode) {
		boolean[] mask = new boolean[lines.size()];
		boolean open = false;
		for (int i = 0; i < lines.size(); i++) {
			open = applyComment(lines.get(i).strip(), open, insideCode[i], mask, i);
		}
		return mask;
	}

	/**
	 * Marks line {@code index} as commented or not and returns whether a comment is
	 * still open afterwards. A line is inert when a comment was already open at its
	 * start, or when its own content opens one it does not close; whether a comment
	 * remains open is read from every delimiter on the line rather than from its
	 * first characters, so a comment opened after text — {@code Superseded: <!--} —
	 * still hides the lines below it.
	 */
	private static boolean applyComment(String line, boolean open, boolean insideCode, boolean[] mask, int index) {
		if (insideCode) {
			return open;
		}
		mask[index] = open || opensBlock(line);
		return remainsOpen(line, 0, open);
	}

	/** True when the line's own content starts a comment that the same line does not close. */
	private static boolean opensBlock(String line) {
		return line.startsWith(COMMENT_START) && remainsOpen(line, 0, false);
	}

	/**
	 * Whether a comment is open once {@code line} has been read from {@code from},
	 * following each delimiter in turn: an open comment looks for its {@code -->},
	 * a closed one for the next {@code <!--}.
	 */
	private static boolean remainsOpen(String line, int from, boolean open) {
		String delimiter = open ? COMMENT_END : COMMENT_START;
		int next = line.indexOf(delimiter, from);
		return next < 0 ? open : remainsOpen(line, next + delimiter.length(), !open);
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

	/** How many times {@code character} repeats at the start of {@code line}. */
	private static int runLength(String line, char character) {
		return (int) line.chars().takeWhile(candidate -> candidate == character).count();
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
