package io.github.adamw7.tools.adopt.step;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.function.Predicate;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * Reshapes the {@code CLAUDE.md} that {@link ClaudeInitStep} generated so it
 * satisfies the {@code claudeMdFormat} rule {@link EnforcerStep} wires into the
 * build. A generic {@code claude init} writes natural, project-specific headings
 * and no {@code AGENTS.md} reference, while the rule demands a fixed set of
 * whole-line headings plus that reference — so without this reshape the adoption
 * fails its own {@link VerifyStep}.
 *
 * <p>The reshape is deterministic and conservative: a near-miss heading is
 * <em>renamed</em> in place so its body survives, only a genuinely absent section
 * is appended, and a required section left empty gets a stub body because the rule
 * fails an empty section just as it fails a missing one. Code — fenced or indented
 * — and commented-out text are left alone, mirroring how the rule matches, and
 * reshaping an already-conforming document is a no-op.
 *
 * <p>Which sections it demands is the caller's to choose, because the guard being
 * wired in differs by build system — see
 * {@link BuildSystem#requiredClaudeMdSections()}. The title and the
 * {@code AGENTS.md} reference are settled either way: the step writes a companion
 * {@code AGENTS.md} for every adopted repository, and a document that never points
 * at it leaves the pair pointing only one way.
 */
public class ClaudeMdConformer {

	/*
	 * These mirror io.github.adamw7.tools.enforcer.doc.ClaudeMdFormatRule in the
	 * claude-code-enforcer module: the title, the AGENTS.md reference, and the
	 * required section headings the wired-in claudeMdFormat rule checks for. They
	 * are duplicated here rather than imported because the adopt module does not
	 * depend on the enforcer module — a pipeline that shipped an enforcer rule
	 * would force the maven-enforcer API on every consumer.
	 *
	 * The copy is not trusted to stay in step: ClaudeMdConformerContractTest runs
	 * the real rule over this class's output, on a test-scoped dependency, so a
	 * rule that asks for one more section fails this module's build rather than
	 * some adopted repository's.
	 */
	static final String TITLE = "# CLAUDE.md";
	static final String AGENTS_REFERENCE = "AGENTS.md";

	/**
	 * The sections the {@code claudeMdFormat} rule demands, and so the sections to
	 * conform to <em>where that rule is the guard being wired in</em> — the Maven
	 * path, {@link MavenBuildSystem#requiredClaudeMdSections()}. They are Java and
	 * Maven sections because the rule is a Java project's rule; a build system whose
	 * guard asks only that the file exist and carry something does not want them, and
	 * says so by requiring none.
	 */
	static final List<String> REQUIRED_SECTIONS = List.of(
			"## Project",
			"## Java version",
			"## Maven",
			"## Principles for Java Development",
			"## Testing",
			"## Dependencies");

	static final String AGENTS_REFERENCE_LINE = "See [AGENTS.md](AGENTS.md) for the companion agent guide.";

	private static final char BACKTICK = '`';
	private static final char TILDE = '~';
	private static final int MIN_FENCE_LENGTH = 3;
	private static final String COMMENT_START = "<!--";
	private static final String COMMENT_END = "-->";
	private static final String STUB_BODY = "See [AGENTS.md](AGENTS.md).";

	private static final char TAB = '\t';
	private static final char SPACE = ' ';

	/** The indent Markdown reads as a code block, and the width a tab advances to. */
	private static final int CODE_INDENT = 4;

	/**
	 * One to six {@code #} characters, then whitespace and any text, or nothing at
	 * all — the ATX heading the rule recognises. Reading a heading any more loosely
	 * than the rule does makes the reshape act on prose the rule holds to be body: a
	 * {@code #1 rule: run mvn install} was taken for a level-one heading that ended
	 * the section above it, so a section that already had a body was given a stub in
	 * front of the prose it already carried.
	 */
	private static final Pattern ATX_HEADING = Pattern.compile("#{1,6}(\\s.*)?");

	/** The blank lines the reshape may leave at the end, dropped so the document keeps a single one. */
	private static final Pattern TRAILING_BLANK_LINES = Pattern.compile("\\n\\s*+\\z");

	private final List<String> requiredSections;

	/** Conforms to the full {@code claudeMdFormat} contract. */
	public ClaudeMdConformer() {
		this(REQUIRED_SECTIONS);
	}

	/**
	 * Conforms only as far as the guard being wired in actually demands. Stamping the
	 * {@code claudeMdFormat} sections onto every adopted repository put
	 * {@code ## Java version}, {@code ## Maven} and
	 * {@code ## Principles for Java Development} — each stubbed with a pointer to
	 * {@code AGENTS.md} — into the {@code CLAUDE.md} of projects that are neither
	 * Java nor Maven, where nothing then checked them: the Gradle and GitHub Actions
	 * guards ask only that the file exist and carry something. The reshape is worth
	 * making only where a guard would otherwise reject the file, so what to require
	 * is the build system's to say.
	 *
	 * @param requiredSections the headings to canonicalize, append and give a body to,
	 *                         empty for a guard that demands no particular section
	 */
	public ClaudeMdConformer(List<String> requiredSections) {
		this.requiredSections = List.copyOf(requiredSections);
	}

	/**
	 * @return {@code content} reshaped so the {@code claudeMdFormat} rule passes,
	 *         with a single trailing newline
	 */
	public String conform(String content) {
		List<String> lines = splitLines(content);
		closeUnterminatedBlocks(lines);
		ensureTitle(lines);
		canonicalizeHeadings(lines);
		ensureRequiredSections(lines);
		ensureAgentsReference(lines);
		return join(lines);
	}

	private List<String> splitLines(String content) {
		return new ArrayList<>(List.of(LineTerminators.normalized(content).split("\n", -1)));
	}

	/**
	 * Closes a fence and an HTML comment the document opened and never closed, so
	 * everything appended below lands outside the block. The fence is closed with a
	 * delimiter that actually closes it, so a {@code ````} wrapper is not left open by
	 * a {@code ```} line. A section appended inside an open comment is inert text the
	 * rule does not see, so the adoption would fail its own {@link VerifyStep} on a
	 * section it had just added. A document whose fences and comments balance is left
	 * untouched, keeping the reshape idempotent.
	 *
	 * <p>The fence is closed first, because a comment inside a fenced code block is
	 * sample text rather than a comment — the same precedence the rule reads them
	 * with — and the outline is taken afresh afterwards so the added line counts.
	 */
	private void closeUnterminatedBlocks(List<String> lines) {
		Delimiter openFence = Outline.of(lines).openFence();
		if (openFence != null) {
			lines.add(openFence.closing());
		}
		if (Outline.of(lines).openComment()) {
			lines.add(COMMENT_END);
		}
	}

	private void ensureTitle(List<String> lines) {
		if (!TITLE.equals(firstNonBlank(lines))) {
			lines.addAll(0, List.of(TITLE, ""));
		}
	}

	private String firstNonBlank(List<String> lines) {
		return lines.stream().map(String::strip).filter(line -> !line.isEmpty()).findFirst().orElse("");
	}

	/**
	 * Renaming a heading in place leaves the line count untouched, so one outline
	 * serves every required section here — unlike {@link #ensureSection}, which
	 * inserts lines and has to read the document afresh each time.
	 */
	private void canonicalizeHeadings(List<String> lines) {
		Outline outline = Outline.of(lines);
		Set<Integer> claimed = reservedHeadings(outline);
		requiredSections.forEach(required -> canonicalize(outline, required, claimed));
	}

	/**
	 * The indices of lines that already are a required heading exactly, reserved so
	 * a near-match search never renames a heading that is already serving another
	 * required section.
	 */
	private Set<Integer> reservedHeadings(Outline outline) {
		return outline.structural(line -> requiredSections.contains(line.strip()))
				.boxed()
				.collect(Collectors.toCollection(LinkedHashSet::new));
	}

	private void canonicalize(Outline outline, String required, Set<Integer> claimed) {
		if (outline.indexOfHeading(required) >= 0) {
			return;
		}
		int index = firstNearMatch(outline, required, claimed);
		if (index >= 0) {
			outline.lines().set(index, required);
			claimed.add(index);
		}
	}

	private int firstNearMatch(Outline outline, String required, Set<Integer> claimed) {
		return outline.structural(line -> isNearMatch(line.strip(), required))
				.filter(index -> !claimed.contains(index))
				.findFirst()
				.orElse(-1);
	}

	/**
	 * A near-miss is the required heading in a different case, or followed by a
	 * space and extra words — the two shapes {@code claude init} produces
	 * ({@code ## Project purpose} for {@code ## Project}). The trailing space keeps
	 * {@code ## Maven} from matching an unrelated {@code ## Mavenish} heading.
	 */
	private static boolean isNearMatch(String stripped, String required) {
		String actual = stripped.toLowerCase(Locale.ROOT);
		String wanted = required.toLowerCase(Locale.ROOT);
		return isHeading(stripped) && (actual.equals(wanted) || actual.startsWith(wanted + " "));
	}

	/**
	 * Gives every required section both a heading and a body: an absent section is
	 * appended with a stub, and a heading the document left bare — typically a
	 * near-miss renamed in place above — has one inserted beneath it. The rule fails
	 * an empty section just as it fails a missing one, so both are settled here.
	 */
	private void ensureRequiredSections(List<String> lines) {
		requiredSections.forEach(required -> ensureSection(lines, required));
	}

	/**
	 * The outline is taken afresh per section because appending a section or
	 * inserting a stub shifts the lines the next one is found at, and the sections
	 * are few enough for that to stay cheap.
	 */
	private void ensureSection(List<String> lines, String required) {
		Outline outline = Outline.of(lines);
		int index = outline.indexOfHeading(required);
		if (index < 0) {
			lines.addAll(List.of("", required, "", STUB_BODY));
		} else if (!outline.hasBody(index)) {
			lines.addAll(index + 1, List.of("", STUB_BODY));
		}
	}

	/** A deeper sub-heading continues the section; one at its level or shallower ends it. */
	private static boolean isBodyLine(String line, int level) {
		return !isHeading(line) || headingLevel(line) > level;
	}

	private static int headingLevel(String heading) {
		return (int) heading.chars().takeWhile(character -> character == '#').count();
	}

	/**
	 * The reference is separated from what follows it only when that line is not
	 * already blank. A document conformed to no required sections keeps the body
	 * {@code claude init} wrote, blank line and all, and closing the insertion with
	 * one of its own left a double blank in the first commit of every repository
	 * whose guard demands no section.
	 *
	 * <p>An existing mention only counts where the rule counts one: its
	 * {@code containsInProse} reads the lines that carry document structure, so a
	 * mention inside a code sample or an HTML comment satisfies it no more than a
	 * commented-out heading satisfies a required section. Asking the looser question
	 * — outside fences alone — let a generated document whose only {@code AGENTS.md}
	 * sat in a comment or an indented sample keep the reference it never had, and the
	 * adoption then failed its own {@link VerifyStep} on the one line it exists to
	 * add.
	 */
	private void ensureAgentsReference(List<String> lines) {
		Outline outline = Outline.of(lines);
		if (outline.structural(line -> line.contains(AGENTS_REFERENCE)).findAny().isPresent()) {
			return;
		}
		int index = outline.titleIndex() + 1;
		List<String> reference = new ArrayList<>(List.of("", AGENTS_REFERENCE_LINE));
		if (!isBlankAt(lines, index)) {
			reference.add("");
		}
		lines.addAll(index, reference);
	}

	private static boolean isBlankAt(List<String> lines, int index) {
		return index < lines.size() && lines.get(index).isBlank();
	}

	private static boolean isHeading(String stripped) {
		return ATX_HEADING.matcher(stripped).matches();
	}

	/**
	 * The document as the reshape reads it: the lines themselves, which of them are
	 * code, and which sit inside an HTML comment. The three travel together because
	 * every search the reshape makes is against one of those masks, and a mask taken
	 * from lines other than the ones it is applied to would quietly mis-answer — so a
	 * reshape that inserts or removes lines takes a fresh outline rather than carrying
	 * this one on.
	 *
	 * <p>Renaming a line in place keeps the outline valid, since the masks are
	 * indexed by line number and the count has not moved.
	 *
	 * @param code        which lines Markdown quotes as code, fenced or indented
	 * @param openFence   the delimiter of a fence the document never closed, or
	 *                    {@code null} when its fences balance
	 * @param openComment whether the document left an HTML comment unterminated
	 */
	private record Outline(List<String> lines, boolean[] code, boolean[] comment, Delimiter openFence,
			boolean openComment) {

		/**
		 * The code mask is settled before the comment scan runs, because a comment
		 * inside code is sample text rather than a comment and so that scan needs the
		 * code verdict for the very line it is on.
		 */
		static Outline of(List<String> lines) {
			boolean[] code = new boolean[lines.size()];
			Delimiter openFence = markCode(lines, code);
			boolean[] comment = new boolean[lines.size()];
			boolean openComment = markComments(lines, code, comment);
			return new Outline(lines, code, comment, openFence, openComment);
		}

		/** The indices of the lines outside code whose text matches, in document order. */
		IntStream matching(Predicate<String> match) {
			return IntStream.range(0, lines.size()).filter(index -> !code[index] && match.test(lines.get(index)));
		}

		/**
		 * The indices of the lines that can carry document structure — outside code and
		 * outside HTML comments alike — whose text matches. A heading is only ever
		 * looked for, and only ever renamed, on one of these, because that is where the
		 * rule recognises one: a section commented out with {@code <!-- ... -->} is
		 * inert text, and counting it left the real section unwritten while the rule
		 * went on demanding it.
		 */
		IntStream structural(Predicate<String> match) {
			return matching(match).filter(index -> !comment[index]);
		}

		/** @return the index of the heading outside code and comments, or {@code -1} when absent */
		int indexOfHeading(String heading) {
			return structural(line -> line.strip().equals(heading)).findFirst().orElse(-1);
		}

		/**
		 * The title is the first non-blank line by the time this is asked, and both a
		 * fence marker and the blank line an indented block opens below would themselves
		 * be lines before it, so the title can never be one the mask covers. A document
		 * with no title line at all falls back to the top.
		 */
		int titleIndex() {
			return Math.max(indexOfHeading(TITLE), 0);
		}

		/**
		 * Mirrors how the rule decides a section is non-empty: before the next heading
		 * at its own level or shallower, the section must carry prose, a code block, or
		 * a deeper sub-heading. Blank lines are neither, so the scan looks past them and
		 * the first line that decides settles the section.
		 */
		boolean hasBody(int headingIndex) {
			int level = headingLevel(lines.get(headingIndex).strip());
			return IntStream.range(headingIndex + 1, lines.size())
					.filter(this::decidesBody)
					.limit(1)
					.anyMatch(index -> code[index] || isBodyLine(lines.get(index).strip(), level));
		}

		/**
		 * Whether the line settles the question of the section's body. A blank line
		 * does not, and neither does a commented-out one: a section whose only content
		 * is inside an HTML comment reads as empty to the rule, which is what commenting
		 * it out meant, so the reshape has to give it a stub rather than conclude it
		 * already has a body and leave the adoption to fail its own {@link VerifyStep}.
		 */
		private boolean decidesBody(int index) {
			return code[index] || (!comment[index] && !lines.get(index).isBlank());
		}
	}

	/**
	 * One fence delimiter line: the character it is written with, how many of them it
	 * runs, and whether anything follows them — the info string of an opening
	 * delimiter, such as the {@code java} of {@code ```java}.
	 *
	 * <p>This mirrors {@code MarkdownDocument} in the {@code claude-code-enforcer}
	 * module, the reader the wired-in {@code claudeMdFormat} rule uses, and is held
	 * to it by the same contract test as {@link #REQUIRED_SECTIONS}. Reading fences
	 * any more loosely
	 * than the rule does makes the reshape act on lines the rule holds to be code: a
	 * {@code ## Testing} inside a code sample was taken for the section the document
	 * already had, so the real one was never appended and the adoption failed its own
	 * {@link VerifyStep} — and a heading inside a sample was renamed in place,
	 * rewriting the sample.
	 */
	private record Delimiter(char character, int length, boolean info) {

		/**
		 * Whether this line closes the block {@code open} started. A closing delimiter
		 * uses the same character, is at least as long, and carries no info string, so a
		 * shorter or annotated fence nested inside the block is content rather than its
		 * end.
		 */
		boolean closes(Delimiter open) {
			return character == open.character() && length >= open.length() && !info;
		}

		/** The delimiter line that closes this one: the same run, carrying no info string. */
		String closing() {
			return String.valueOf(character).repeat(length);
		}
	}

	/**
	 * Marks every line Markdown quotes as code, both ways it allows: the fenced
	 * blocks first, then the indented ones over the same mask, so a fence's own lines
	 * are already spoken for and an indent inside one is content rather than a block
	 * of its own. This mirrors {@code MarkdownDocument} in the
	 * {@code claude-code-enforcer} module, as {@link Delimiter} does; keep the two in
	 * sync.
	 *
	 * @return the fence delimiter still open at the end of the document, or
	 *         {@code null} when its fences balance
	 */
	private static Delimiter markCode(List<String> lines, boolean[] mask) {
		Delimiter openFence = markFences(lines, mask);
		markIndentedCode(lines, mask);
		return openFence;
	}

	private static Delimiter markFences(List<String> lines, boolean[] mask) {
		Delimiter open = null;
		for (int index = 0; index < lines.size(); index++) {
			open = applyFence(lines.get(index).strip(), open, mask, index);
		}
		return open;
	}

	/**
	 * Marks the code Markdown quotes the other way it allows — by indenting it four
	 * columns, a tab counting on to the next four-column stop. The rule has always
	 * read those lines as code, and reading them as prose here made the reshape act
	 * on a sample: a {@code ## Testing} shown as an indented example was taken for the
	 * section the document already had, so the real one was never appended and the
	 * adoption failed its own {@link VerifyStep} — and a near-miss heading inside one
	 * was renamed in place, rewriting the sample and losing its indentation.
	 */
	private static void markIndentedCode(List<String> lines, boolean[] mask) {
		boolean mayOpen = true;
		for (int index = 0; index < lines.size(); index++) {
			mayOpen = applyIndent(lines.get(index), mayOpen, mask, index);
		}
	}

	/**
	 * Marks whether the line is indented code and returns whether an indented line
	 * below it would open a block. Only a blank line, a line already masked as code,
	 * and the start of the document leave that open: an indented line below a
	 * paragraph is a lazy continuation of that paragraph rather than code. A blank
	 * line is left unmarked, since it carries nothing to read either way.
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

	private static boolean markComments(List<String> lines, boolean[] code, boolean[] mask) {
		boolean open = false;
		for (int index = 0; index < lines.size(); index++) {
			open = applyComment(lines.get(index).strip(), open, code[index], mask, index);
		}
		return open;
	}

	/**
	 * Marks whether the line is commented out and returns whether a comment is still
	 * open after it. A comment that opens and closes on one line masks nothing —
	 * {@code <!-- ## Testing -->} is not a heading to begin with — while a block
	 * comment hides the lines it spans from every heading search.
	 *
	 * <p>Whether a comment remains open is read from every delimiter on the line
	 * rather than from its first characters, the reading the rule takes: a comment
	 * opened after text — {@code Superseded: <!--} — hides the lines below it, and a
	 * line that closes one comment and opens another leaves one open. Asking only
	 * whether the line began with {@value #COMMENT_START} left those lines visible to
	 * the reshape and invisible to the rule, so a required section the document had
	 * commented out was counted as present, never appended, and the adoption failed
	 * its own {@link VerifyStep}.
	 *
	 * @param insideCode whether the line is code, because a comment inside a code
	 *                   block is sample text rather than a comment: the code wins
	 */
	private static boolean applyComment(String line, boolean open, boolean insideCode, boolean[] mask, int index) {
		if (insideCode) {
			return open;
		}
		mask[index] = open || opensBlock(line);
		return remainsOpen(line, 0, open);
	}

	/** Whether the line's own content starts a comment that the same line does not close. */
	private static boolean opensBlock(String line) {
		return line.startsWith(COMMENT_START) && remainsOpen(line, 0, false);
	}

	/**
	 * Whether a comment is open once {@code line} has been read from {@code from},
	 * following each delimiter in turn: an open comment looks for its
	 * {@value #COMMENT_END}, a closed one for the next {@value #COMMENT_START}.
	 */
	private static boolean remainsOpen(String line, int from, boolean open) {
		String delimiter = open ? COMMENT_END : COMMENT_START;
		int next = line.indexOf(delimiter, from);
		return next < 0 ? open : remainsOpen(line, next + delimiter.length(), !open);
	}

	/**
	 * Marks whether the line is code and returns the fence delimiter still open after
	 * it. A fence is closed only by a delimiter that {@link Delimiter#closes} it, so a
	 * {@code ~~~} line inside a {@code ```} block, a {@code ```java} line inside a
	 * {@code ```} block, and a {@code ```} line inside a {@code ````} wrapper all stay
	 * content.
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

	/** @return the fence delimiter the line declares, or {@code null} when it declares none */
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
		return (int) line.chars().takeWhile(candidate -> candidate == character).count();
	}

	/** Joins the lines under a single trailing newline, dropping any blank lines the reshape left at the end. */
	private String join(List<String> lines) {
		return TRAILING_BLANK_LINES.matcher(String.join("\n", lines)).replaceAll("") + "\n";
	}
}
