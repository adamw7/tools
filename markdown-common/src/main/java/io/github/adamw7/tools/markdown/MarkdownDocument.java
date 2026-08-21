package io.github.adamw7.tools.markdown;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

/**
 * A parsed Markdown document: its lines plus the masks marking which of them are
 * code and which sit inside an HTML comment. Parsing the masks once, at
 * construction, lets the structural checks share them instead of each rebuilding
 * them.
 * <p>
 * Two kinds of caller read a document through this class and must agree about it:
 * one that <em>judges</em> a document — the {@code claudeMdFormat} enforcer rule
 * asking whether a {@code CLAUDE.md} carries a required section — and one that
 * <em>reshapes</em> a document so that judgement passes, which is what the
 * adoption pipeline's {@code ClaudeMdConformer} does before it commits a generated
 * {@code CLAUDE.md}. Two spellings of these readings drifted apart in every way
 * they could, each time producing the same bug: the rewriter acted on lines the
 * rule holds to be code, or concluded a section was missing that the rule reads as
 * present, and the adoption then failed the very check it exists to satisfy —
 * after committing and pushing the file. That is why the reader is one class in a
 * module of its own rather than a copy on each side.
 * <p>
 * Structure — a heading — is only ever recognised outside code and outside an HTML
 * comment, so a heading shown in a sample or commented out is not structure. Code
 * is quoted both ways Markdown allows, and the two are read in a single pass
 * because each decides what the other may read: a fence shown inside an indented
 * block is the delimiter that block illustrates rather than one this document
 * opens. A fence closes only on a run of the same character, at least as long, and
 * carrying no info string, so the nested fences documentation about Markdown
 * writes stay content. An indented block opens only where one may, after a blank
 * line, and its four columns are measured from the enclosing list item rather than
 * from the margin, so a paragraph continuing a list item is prose however deep the
 * item is nested.
 * <p>
 * Each of those readings is stated where it is implemented, together with what
 * misreading it silently reclassified.
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

	/**
	 * The marker opening a list item, with the whitespace that separates it from the
	 * item's content: a bullet, or an ordered marker, indented too little to be code
	 * itself. What the match is wanted for is its width — the column the item's
	 * content, and so its continuation paragraphs, are indented to.
	 */
	private static final Pattern LIST_MARKER = Pattern.compile("[ \\t]{0,3}(?:[-+*]|\\d{1,9}[.)])(?:[ \\t]+|$)");

	private final List<String> lines;
	private final boolean[] insideCode;
	private final boolean[] insideComment;
	private final Delimiter openFence;
	private final boolean openComment;

	private MarkdownDocument(List<String> lines, CodeScan code, CommentScan comment) {
		this.lines = lines;
		this.insideCode = code.mask();
		this.openFence = code.openFence();
		this.insideComment = comment.mask();
		this.openComment = comment.open();
	}

	/** Parses {@code content} into lines, a code mask, and an HTML-comment mask. */
	public static MarkdownDocument parse(String content) {
		return of(content.lines().toList());
	}

	/**
	 * Parses lines a caller has already split, for one that rewrites a document and
	 * so cannot let the split be re-decided under it: {@link String#lines()} drops
	 * the empty line a document's trailing newline leaves, which a rewriter that
	 * appends to those lines has to keep. The lines are copied, so the document is a
	 * snapshot of the list as it is now — a caller that inserts or removes lines
	 * parses again rather than carrying this one on.
	 */
	public static MarkdownDocument of(List<String> lines) {
		List<String> snapshot = List.copyOf(lines);
		CodeScan code = codeScan(snapshot);
		return new MarkdownDocument(snapshot, code, commentScan(snapshot, code.mask()));
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

	/**
	 * The delimiter line that closes a fence this document opened and never closed,
	 * or empty when its fences balance. The run is as long as the one that opened
	 * the block and carries no info string, so a {@code ````} wrapper is not left
	 * open by a {@code ```} line.
	 * <p>
	 * A rewriter asks this before it appends anything, because a section appended
	 * inside an open fence is code as far as every check above is concerned — the
	 * document would come back still missing the section that had just been added
	 * to it.
	 */
	public Optional<String> openFenceTerminator() {
		return Optional.ofNullable(openFence).map(Delimiter::closing);
	}

	/**
	 * The delimiter that closes an HTML comment this document opened and never
	 * closed, or empty when its comments balance. Asked for the same reason as
	 * {@link #openFenceTerminator}: text appended inside an open comment is inert,
	 * so a check reads it as absent.
	 */
	public Optional<String> openCommentTerminator() {
		return openComment ? Optional.of(COMMENT_END) : Optional.empty();
	}

	/**
	 * The indices of the lines that can carry document structure — outside code and
	 * outside HTML comments alike — whose raw text {@code match} accepts, in
	 * document order. A heading is only ever recognised, and only ever rewritten, on
	 * one of these: a section commented out with {@code <!-- ... -->} is inert text,
	 * and counting it lets a document satisfy a required-section check with the very
	 * heading its author had removed.
	 */
	public IntStream structuralLines(Predicate<String> match) {
		return structuralLines().filter(index -> match.test(lines.get(index)));
	}

	private IntStream structuralLines() {
		return IntStream.range(0, lines.size())
				.filter(index -> !insideCode[index] && !insideComment[index]);
	}

	/**
	 * True when {@code token} appears on a line that carries document structure:
	 * outside code and outside HTML comments alike. A commented-out mention is inert
	 * both ways round — it must not satisfy a check that demands the token, and must
	 * not trip one that forbids it.
	 * <p>
	 * An inline code span does count as a mention, because naming a file in
	 * backticks is how prose names a file: a document that says "see
	 * {@code `AGENTS.md`}" has referenced it. A check that <em>forbids</em> a token
	 * cannot read a span that way and asks {@link #containsUnquoted} instead.
	 */
	public boolean containsInProse(String token) {
		return structuralLines().anyMatch(index -> lines.get(index).contains(token));
	}

	/**
	 * True when {@code token} appears on a line that carries document structure and
	 * outside an inline code span. A token quoted as code is the document
	 * illustrating the token rather than writing one, exactly as a fenced sample is:
	 * a document that forbids {@code TODO} must still be able to say "never leave a
	 * {@code `TODO`} behind", which the line-level reading of {@link #containsInProse}
	 * reported as the very thing it forbids.
	 */
	public boolean containsUnquoted(String token) {
		return structuralLines()
				.anyMatch(index -> MarkdownText.withoutCodeSpans(lines.get(index)).contains(token));
	}

	/** The headings outside fenced code blocks and HTML comments, in document order. */
	public Set<String> headings() {
		return headingTexts().collect(Collectors.toCollection(LinkedHashSet::new));
	}

	/** The heading each structural line declares, canonicalised by {@link #headingOf}. */
	private Stream<String> headingTexts() {
		return structuralLines()
				.mapToObj(index -> lines.get(index))
				.map(MarkdownDocument::headingOf)
				.flatMap(Optional::stream);
	}

	/**
	 * The headings from {@code wanted} that are present, in the order they first
	 * appear in the document, each listed once. A required heading that appears
	 * more than once is reported by its first occurrence, so the order comparison
	 * matches the de-duplicated set of present sections rather than reporting a
	 * spurious out-of-order failure.
	 * <p>
	 * Only a heading answers, which is what keeps this in step with
	 * {@link #headings()}: reading every structural line let an entry of
	 * {@code wanted} that is no heading — {@code Testing} rather than
	 * {@code ## Testing} — be answered by a line of prose.
	 */
	public List<String> headingsInOrder(List<String> wanted) {
		Set<String> required = new LinkedHashSet<>(wanted);
		return headingTexts().filter(required::remove).toList();
	}

	/**
	 * @return the index of the first line declaring {@code section}, outside code and
	 *         comments, or {@code -1} when the document declares it nowhere. The
	 *         <em>first</em> answers, because that is the copy every other reading
	 *         here takes when a document carries the section twice.
	 */
	public int headingIndex(String section) {
		return headingIndices(section).findFirst().orElse(-1);
	}

	/**
	 * Every structural line declaring {@code section}, in document order. A document
	 * that carries a heading more than once is a document a rewriter has to see
	 * whole: only the first counts as the section, so the rest are duplicates it may
	 * have to remove.
	 */
	public IntStream headingIndices(String section) {
		return structuralLines(line -> declares(line, section));
	}

	/** True when {@code line} is the heading {@code section} names. */
	private static boolean declares(String line, String section) {
		return headingOf(line).filter(section::equals).isPresent();
	}

	/**
	 * True when the heading at {@code headingIndex} is followed, before the next
	 * heading at its own level or shallower, by any prose, a code block, or a deeper
	 * sub-heading. A caller locates the heading with {@link #headingIndex} first, so
	 * a missing section and an empty one are told apart by one walk of the document
	 * rather than by one walk each.
	 * <p>
	 * The first line that settles the question decides it; a section nothing settles
	 * has no body.
	 */
	public boolean hasBodyAt(int headingIndex) {
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

	/**
	 * The heading {@code line} declares, written canonically — its {@code #} run,
	 * then a single space and the text it carries, or the run alone when it carries
	 * none — or empty when the line declares no heading at all. A heading is the text
	 * it names, not the line it was typed on, so the surrounding whitespace, the
	 * separator (a tab counts) and the closing {@code #} run an author may balance a
	 * heading with are spelling rather than content; comparing raw lines instead made
	 * a checker and a rewriter disagree about which lines are the required sections.
	 * <p>
	 * Whether the line is <em>structure</em> is a separate question this does not
	 * answer — a {@code ## Testing} inside a code sample still reads as a heading
	 * here. Callers pair this with {@link #structuralLines}, as {@link #headingIndices}
	 * does.
	 */
	public static Optional<String> headingOf(String line) {
		String stripped = line.strip();
		return isHeading(stripped) ? Optional.of(headingText(stripped)) : Optional.empty();
	}

	private static String headingText(String line) {
		int level = headingLevel(line);
		String hashes = line.substring(0, level);
		String text = withoutClosingRun(line.substring(level).strip());
		return text.isEmpty() ? hashes : hashes + SPACE + text;
	}

	/**
	 * The heading's text without the {@code #} run that closes it. The run only
	 * closes a heading when whitespace precedes it, so {@code ## C#} keeps its hash
	 * while {@code ## Testing ##} loses both of its; a text that is nothing but
	 * hashes — the {@code ###} of {@code ## ###} — is the closing run itself and
	 * leaves an empty heading.
	 */
	private static String withoutClosingRun(String text) {
		int end = text.length();
		while (end > 0 && text.charAt(end - 1) == HEADING_CHAR) {
			end--;
		}
		if (end == text.length()) {
			return text;
		}
		return end == 0 || isIndent(text.charAt(end - 1)) ? text.substring(0, end).strip() : text;
	}

	private static int headingLevel(String heading) {
		return runLength(heading, HEADING_CHAR);
	}

	/** What one pass over the document found: the mask, and the fence it left open. */
	private record CodeScan(boolean[] mask, Delimiter openFence) {
	}

	/** What one pass over the document found: the mask, and whether a comment is still open. */
	private record CommentScan(boolean[] mask, boolean open) {
	}

	/**
	 * Marks every line Markdown reads as code, both ways it is quoted, in one left
	 * to right pass, so a fence's own lines are spoken for before an indent is read
	 * into them and an indented block's lines before a fence is.
	 */
	private static CodeScan codeScan(List<String> lines) {
		boolean[] mask = new boolean[lines.size()];
		Scan scan = Scan.start();
		for (int i = 0; i < lines.size(); i++) {
			scan = scan.read(lines.get(i), mask, i);
		}
		return new CodeScan(mask, scan.fence());
	}

	/** The line's indent in columns, a tab counting on to the next four-column stop. */
	private static int indentWidth(String line) {
		int index = 0;
		while (index < line.length() && isIndent(line.charAt(index))) {
			index++;
		}
		return widthOf(line, index);
	}

	/**
	 * The display width of the line's first {@code length} characters, a tab counting
	 * on to the next four-column stop. Both the indent that quotes code and the marker
	 * that indents a list item's content are measured in these columns, so a tabbed
	 * list is read the same way a spaced one is.
	 */
	private static int widthOf(String line, int length) {
		int width = 0;
		for (int index = 0; index < length; index++) {
			width += line.charAt(index) == TAB ? CODE_INDENT - width % CODE_INDENT : 1;
		}
		return width;
	}

	private static boolean isIndent(char character) {
		return character == SPACE || character == TAB;
	}

	/**
	 * The column a list item's content is indented to when {@code line} opens one, or
	 * {@link Scan#NO_LIST} when it opens none. A thematic break is not a list: its
	 * {@code ---} carries no whitespace after the first dash, which is what separates a
	 * marker from the content it introduces.
	 */
	private static int listContentIndent(String line) {
		Matcher marker = LIST_MARKER.matcher(line);
		return marker.lookingAt() ? widthOf(line, marker.end()) : Scan.NO_LIST;
	}

	/**
	 * Marks the lines an HTML comment spans, so a commented-out section is not read
	 * as document structure. A comment that opens and closes on one line masks
	 * nothing — {@code <!-- ## Testing -->} is not a heading to begin with. A comment
	 * inside code is sample text, so the code wins.
	 */
	private static CommentScan commentScan(List<String> lines, boolean[] insideCode) {
		boolean[] mask = new boolean[lines.size()];
		boolean open = false;
		for (int i = 0; i < lines.size(); i++) {
			open = applyComment(lines.get(i), open, insideCode[i], mask, i);
		}
		return new CommentScan(mask, open);
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
		String read = asRead(line, open);
		mask[index] = open || opensBlock(read);
		return remainsOpen(read, open);
	}

	/**
	 * The line as the comment scan reads it. Outside a comment its inline code spans
	 * are dropped first, because a delimiter quoted as code is the document
	 * illustrating one rather than writing one — the same reading
	 * {@link #containsUnquoted} takes, and the reading a fence already gets from
	 * being matched on whole lines. Documentation about Markdown writes
	 * {@code `<!--`} in prose precisely because it is an example, and taking it for a
	 * real delimiter opened a comment nothing closed, masking the rest of the
	 * document as inert.
	 * <p>
	 * Inside a comment there are no code spans to honour: the text is already inert,
	 * so its backticks are ordinary characters and a {@code -->} among them closes
	 * the block exactly as one anywhere else on the line does.
	 */
	private static String asRead(String line, boolean open) {
		return (open ? line : MarkdownText.withoutCodeSpans(line)).strip();
	}

	/** True when the line's own content starts a comment that the same line does not close. */
	private static boolean opensBlock(String line) {
		return line.startsWith(COMMENT_START) && remainsOpen(line, false);
	}

	/**
	 * Whether a comment is open once the whole of {@code line} has been read,
	 * following each delimiter in turn: an open comment looks for its {@code -->},
	 * a closed one for the next {@code <!--}.
	 * <p>
	 * The delimiters are followed in a loop rather than by recursing on the rest of
	 * the line, because the line is content this reader does not control: a generated
	 * or minified line carrying a few thousand delimiters recursed once per delimiter
	 * and overflowed the stack. A {@link StackOverflowError} is no kind of verdict —
	 * it aborts the Maven build as an internal error, and being no
	 * {@code RuntimeException} it escapes the adoption's batch loop as well.
	 */
	private static boolean remainsOpen(String line, boolean open) {
		boolean inside = open;
		int next = line.indexOf(commentDelimiter(inside));
		while (next >= 0) {
			int after = next + commentDelimiter(inside).length();
			inside = !inside;
			next = line.indexOf(commentDelimiter(inside), after);
		}
		return inside;
	}

	/** What the scan looks for next: an open comment its end, a closed one the next start. */
	private static String commentDelimiter(boolean open) {
		return open ? COMMENT_END : COMMENT_START;
	}

	/**
	 * The state one pass over the document carries: the fence delimiter still open,
	 * whether an indented line would open a code block, and the list item an indent is
	 * measured against. Only a blank line, a line already read as code, and the start
	 * of the document leave the second open — an indented line below a paragraph is a
	 * lazy continuation of it, which is what stops an indented code block interrupting
	 * one.
	 */
	private record Scan(Delimiter fence, boolean mayIndent, int listIndent) {

		/** No list is open, so an indented line quotes code from the margin. */
		static final int NO_LIST = -1;

		static Scan start() {
			return new Scan(null, true, NO_LIST);
		}

		/**
		 * Marks line {@code index} and answers the state the next line is read in. An
		 * open fence claims the line first, then a blank line, which carries nothing to
		 * read either way and so is left unmarked; then the indent, which speaks for the
		 * line whether or not a block may open at it, and only a line the indent left
		 * alone is read as a fence delimiter.
		 */
		Scan read(String line, boolean[] mask, int index) {
			if (fence != null) {
				return insideFence(line, mask, index);
			}
			if (line.isBlank()) {
				return new Scan(null, true, listIndent);
			}
			if (indentWidth(line) >= codeIndent()) {
				return atIndent(mask, index);
			}
			return atDelimiter(line, mask, index);
		}

		/**
		 * The line is indented too deep to be read as anything but its indent: code
		 * where a block may open, and otherwise the lazy continuation of the paragraph
		 * above it — prose either way rather than markup.
		 * <p>
		 * That it is not a fence delimiter is the point. A fence opener may be indented
		 * at most three columns past its container, so a {@code ```} shown four columns
		 * in below a paragraph opens nothing. Falling through to {@link #atDelimiter}
		 * read one as a fence the document opened and never closed, masking the rest of
		 * the document — its remaining headings included — as code.
		 */
		private Scan atIndent(boolean[] mask, int index) {
			mask[index] = mayIndent;
			return new Scan(null, mayIndent, listIndent);
		}

		/**
		 * The column an indented code block opens at: four, or four past the content of
		 * the list item the line sits in. A blank line does not close a list, so this
		 * still stands over the gap between an item and its continuation paragraph.
		 */
		private int codeIndent() {
			return listIndent == NO_LIST ? CODE_INDENT : listIndent + CODE_INDENT;
		}

		private Scan insideFence(String line, boolean[] mask, int index) {
			mask[index] = true;
			Delimiter delimiter = delimiterOf(line.strip());
			return new Scan(delimiter != null && delimiter.closes(fence) ? null : fence, true, listIndent);
		}

		private Scan atDelimiter(String line, boolean[] mask, int index) {
			Delimiter delimiter = delimiterOf(line.strip());
			mask[index] = delimiter != null;
			return new Scan(delimiter, delimiter != null, listAfter(line));
		}

		/**
		 * The list this line leaves open: the one its own marker opens, the one it
		 * continues by staying indented to that item's content, or none when it falls
		 * back to the margin and so ends the list.
		 */
		private int listAfter(String line) {
			int opened = listContentIndent(line);
			if (opened != NO_LIST) {
				return opened;
			}
			return indentWidth(line) >= listIndent ? listIndent : NO_LIST;
		}
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

		/** The delimiter line that closes this one: the same run, carrying no info string. */
		String closing() {
			return String.valueOf(character).repeat(length);
		}
	}
}
