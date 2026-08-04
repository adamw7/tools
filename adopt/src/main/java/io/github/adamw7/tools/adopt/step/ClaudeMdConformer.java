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
 * fails an empty section just as it fails a missing one. Fenced code and
 * commented-out text are left alone, mirroring how the rule matches, and reshaping
 * an already-conforming document is a no-op.
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

	/**
	 * @return {@code content} reshaped so the {@code claudeMdFormat} rule passes,
	 *         with a single trailing newline
	 */
	public String conform(String content) {
		List<String> lines = splitLines(content);
		closeUnterminatedFence(lines);
		closeUnterminatedComment(lines);
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
	 * Closes a fence the document opened and never closed — with a delimiter that
	 * actually closes it, so a {@code ````} wrapper is not left open by a {@code ```}
	 * line — so everything appended below lands outside the block. A document whose
	 * fences balance is returned untouched, keeping the reshape idempotent.
	 */
	private void closeUnterminatedFence(List<String> lines) {
		Delimiter open = scanFences(lines).openAtEnd();
		if (open != null) {
			lines.add(open.closing());
		}
	}

	/**
	 * Closes an HTML comment the document opened and never closed, for the same
	 * reason {@link #closeUnterminatedFence} closes a fence: everything appended
	 * below has to land outside the block. A section appended inside an open comment
	 * is inert text the rule does not see, so the adoption would fail its own
	 * {@link VerifyStep} on a section it had just added. A document whose comments
	 * balance is returned untouched, keeping the reshape idempotent.
	 *
	 * <p>Fences are closed first, because a comment inside a fenced code block is
	 * sample text rather than a comment — the same precedence the rule reads them
	 * with.
	 */
	private void closeUnterminatedComment(List<String> lines) {
		if (scanComments(lines, scanFences(lines).mask()).openAtEnd()) {
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
		REQUIRED_SECTIONS.forEach(required -> canonicalize(outline, required, claimed));
	}

	/**
	 * The indices of lines that already are a required heading exactly, reserved so
	 * a near-match search never renames a heading that is already serving another
	 * required section.
	 */
	private Set<Integer> reservedHeadings(Outline outline) {
		return outline.structural(line -> REQUIRED_SECTIONS.contains(line.strip()))
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
		REQUIRED_SECTIONS.forEach(required -> ensureSection(lines, required));
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

	private void ensureAgentsReference(List<String> lines) {
		Outline outline = Outline.of(lines);
		if (outline.matching(line -> line.contains(AGENTS_REFERENCE)).findAny().isEmpty()) {
			lines.addAll(outline.titleIndex() + 1, List.of("", AGENTS_REFERENCE_LINE, ""));
		}
	}

	private static boolean isHeading(String stripped) {
		return ATX_HEADING.matcher(stripped).matches();
	}

	/**
	 * The document as the reshape reads it: the lines themselves, which of them sit
	 * inside a code fence, and which sit inside an HTML comment. The three travel
	 * together because every search the reshape makes is against one of those masks,
	 * and a mask taken from lines other than the ones it is applied to would quietly
	 * mis-answer — so a reshape that inserts or removes lines takes a fresh outline
	 * rather than carrying this one on.
	 *
	 * <p>Renaming a line in place keeps the outline valid, since the masks are
	 * indexed by line number and the count has not moved.
	 */
	private record Outline(List<String> lines, boolean[] fence, boolean[] comment) {

		static Outline of(List<String> lines) {
			boolean[] fence = scanFences(lines).mask();
			return new Outline(lines, fence, scanComments(lines, fence).mask());
		}

		/** The indices of the lines outside code fences whose text matches, in document order. */
		IntStream matching(Predicate<String> match) {
			return IntStream.range(0, lines.size()).filter(index -> !fence[index] && match.test(lines.get(index)));
		}

		/**
		 * The indices of the lines that can carry document structure — outside code
		 * fences and outside HTML comments alike — whose text matches. A heading is
		 * only ever looked for, and only ever renamed, on one of these, because that
		 * is where the rule recognises one: a section commented out with
		 * {@code <!-- ... -->} is inert text, and counting it left the real section
		 * unwritten while the rule went on demanding it.
		 */
		IntStream structural(Predicate<String> match) {
			return matching(match).filter(index -> !comment[index]);
		}

		/** @return the index of the heading outside code fences and comments, or {@code -1} when absent */
		int indexOfHeading(String heading) {
			return structural(line -> line.strip().equals(heading)).findFirst().orElse(-1);
		}

		/**
		 * The title is the first non-blank line by the time this is asked, and a fence
		 * marker would itself be a non-blank line before it, so the title can never be
		 * one the mask covers. A document with no title line at all falls back to the top.
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
					.anyMatch(index -> fence[index] || isBodyLine(lines.get(index).strip(), level));
		}

		/**
		 * Whether the line settles the question of the section's body. A blank line
		 * does not, and neither does a commented-out one: a section whose only content
		 * is inside an HTML comment reads as empty to the rule, which is what commenting
		 * it out meant, so the reshape has to give it a stub rather than conclude it
		 * already has a body and leave the adoption to fail its own {@link VerifyStep}.
		 */
		private boolean decidesBody(int index) {
			return fence[index] || (!comment[index] && !lines.get(index).isBlank());
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
	 * The outcome of reading the document's fences: which lines are code, and the
	 * delimiter still open once the last line has been read.
	 *
	 * @param openAtEnd the unterminated fence's delimiter, or {@code null} when the
	 *                  document's fences balance
	 */
	private record Fences(boolean[] mask, Delimiter openAtEnd) {
	}

	/**
	 * The outcome of reading the document's HTML comments: which lines are commented
	 * out, and whether one is still open once the last line has been read.
	 *
	 * @param openAtEnd whether the document left a comment unterminated
	 */
	private record Comments(boolean[] mask, boolean openAtEnd) {
	}

	/**
	 * Reads which lines an HTML comment spans. A comment that opens and closes on one
	 * line masks nothing — {@code <!-- ## Testing -->} is not a heading to begin with
	 * — while a block comment hides the lines it spans from every heading search.
	 * This mirrors {@code MarkdownDocument} in the {@code claude-code-enforcer}
	 * module, as {@link Delimiter} does; keep the two in sync.
	 *
	 * @param fence the fence mask, because a comment inside a fenced code block is
	 *              sample text rather than a comment: the fence wins
	 */
	private static Comments scanComments(List<String> lines, boolean[] fence) {
		boolean[] mask = new boolean[lines.size()];
		boolean open = false;
		for (int index = 0; index < lines.size(); index++) {
			open = applyComment(lines.get(index).strip(), open, fence[index], mask, index);
		}
		return new Comments(mask, open);
	}

	/**
	 * Marks whether the line is commented out and returns whether a comment is still
	 * open after it.
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

	private static Fences scanFences(List<String> lines) {
		boolean[] mask = new boolean[lines.size()];
		Delimiter open = null;
		for (int index = 0; index < lines.size(); index++) {
			open = applyFence(lines.get(index).strip(), open, mask, index);
		}
		return new Fences(mask, open);
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
