package io.github.adamw7.tools.adopt.step;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.function.Predicate;
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
 * fails an empty section just as it fails a missing one. Fenced code is left
 * alone, mirroring how the rule matches, and reshaping an already-conforming
 * document is a no-op.
 */
public class ClaudeMdConformer {

	/*
	 * These mirror io.github.adamw7.tools.enforcer.doc.ClaudeMdFormatRule in the
	 * claude-code-enforcer module: the title, the AGENTS.md reference, and the
	 * required section headings the wired-in claudeMdFormat rule checks for. They
	 * are duplicated here rather than imported because the adopt module does not
	 * depend on the enforcer module; keep the two in sync.
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

	private static final String BACKTICK_FENCE = "```";
	private static final String TILDE_FENCE = "~~~";
	private static final String STUB_BODY = "See [AGENTS.md](AGENTS.md).";

	/**
	 * @return {@code content} reshaped so the {@code claudeMdFormat} rule passes,
	 *         with a single trailing newline
	 */
	public String conform(String content) {
		List<String> lines = splitLines(content);
		closeUnterminatedFence(lines);
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
	 * Closes a fence the document opened and never closed — with the marker it was
	 * opened with — so everything appended below lands outside the block. A document
	 * whose fences balance is returned untouched, keeping the reshape idempotent.
	 */
	private void closeUnterminatedFence(List<String> lines) {
		String open = scanFences(lines).openAtEnd();
		if (open != null) {
			lines.add(open);
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
		return outline.matching(line -> REQUIRED_SECTIONS.contains(line.strip()))
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
		return outline.matching(line -> isNearMatch(line.strip(), required))
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
		return stripped.startsWith("#");
	}

	/**
	 * The document as the reshape reads it: the lines themselves, and which of them
	 * sit inside a code fence. The two travel together because every search the
	 * reshape makes is "outside fences", and a mask taken from lines other than the
	 * ones it is applied to would quietly mis-answer — so a reshape that inserts or
	 * removes lines takes a fresh outline rather than carrying this one on.
	 *
	 * <p>Renaming a line in place keeps the outline valid, since the mask is indexed
	 * by line number and the count has not moved.
	 */
	private record Outline(List<String> lines, boolean[] fence) {

		static Outline of(List<String> lines) {
			return new Outline(lines, scanFences(lines).mask());
		}

		/** The indices of the lines outside code fences whose text matches, in document order. */
		IntStream matching(Predicate<String> match) {
			return IntStream.range(0, lines.size()).filter(index -> !fence[index] && match.test(lines.get(index)));
		}

		/** @return the index of the heading outside code fences, or {@code -1} when absent */
		int indexOfHeading(String heading) {
			return matching(line -> line.strip().equals(heading)).findFirst().orElse(-1);
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
					.filter(index -> fence[index] || !lines.get(index).isBlank())
					.limit(1)
					.anyMatch(index -> fence[index] || isBodyLine(lines.get(index).strip(), level));
		}
	}

	/**
	 * The outcome of reading the document's fences: which lines are code, and the
	 * marker still open once the last line has been read.
	 *
	 * @param openAtEnd the unterminated fence's marker, or {@code null} when the
	 *                  document's fences balance
	 */
	private record Fences(boolean[] mask, String openAtEnd) {
	}

	private static Fences scanFences(List<String> lines) {
		boolean[] mask = new boolean[lines.size()];
		String open = null;
		for (int index = 0; index < lines.size(); index++) {
			open = applyFence(lines.get(index).strip(), open, mask, index);
		}
		return new Fences(mask, open);
	}

	/**
	 * Marks whether the line is code and returns the fence marker still open after
	 * it. A fence is closed only by the marker it was opened with, so a {@code ~~~}
	 * line inside a {@code ```} block stays content.
	 */
	private static String applyFence(String line, String open, boolean[] mask, int index) {
		String marker = fenceMarker(line);
		if (open == null) {
			mask[index] = marker != null;
			return marker;
		}
		mask[index] = true;
		return open.equals(marker) ? null : open;
	}

	private static String fenceMarker(String line) {
		if (line.startsWith(BACKTICK_FENCE)) {
			return BACKTICK_FENCE;
		}
		if (line.startsWith(TILDE_FENCE)) {
			return TILDE_FENCE;
		}
		return null;
	}

	/** Joins the lines under a single trailing newline, dropping any blank lines the reshape left at the end. */
	private String join(List<String> lines) {
		return String.join("\n", lines).replaceAll("\\n\\s*+\\z", "") + "\n";
	}
}
