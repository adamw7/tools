package io.github.adamw7.tools.adopt.step;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * Reshapes the {@code CLAUDE.md} that {@link ClaudeInitStep} generated so it
 * satisfies the {@code claudeMdFormat} rule {@link EnforcerStep} wires into the
 * build. A generic {@code claude init} writes natural, project-specific headings
 * ({@code ## Project purpose}, {@code ## Maven module structure}) and no
 * {@code AGENTS.md} reference, but the rule demands a fixed set of whole-line
 * headings plus that reference — so without this reshape the adoption fails its
 * own {@link VerifyStep}.
 *
 * <p>The reshape is deterministic and conservative. It guarantees the
 * {@code # CLAUDE.md} title is the first non-blank line, that {@code AGENTS.md}
 * is referenced, and that every required section heading is present with a body.
 * A heading that is a near-miss of a required one — the required heading followed
 * by extra words, or the same text in a different case — is <em>renamed</em> in
 * place, preserving the content beneath it; only a required heading with no such
 * near-miss is appended as a fresh stub. A required heading that ends up with an
 * empty section — a renamed near-miss the generated document left bare, or a
 * heading immediately followed by its sibling — gets a stub body, because the
 * rule fails an empty section just as it fails a missing one. Headings inside
 * fenced code blocks are left alone, mirroring how the rule matches, so a
 * {@code ##} line in a code sample is never mistaken for document structure.
 *
 * <p>A fence the generated document opened but never closed is closed first, so
 * the sections appended below it are document structure rather than more code.
 * Without that the rule — which reads fences exactly the same way — sees every
 * appended heading as part of the unterminated block and reports all of them
 * missing, and each re-run appends another unreachable copy.
 *
 * <p>Running the reshape again on an already-conforming document is a no-op
 * (beyond normalising a missing or doubled trailing newline), so re-adopting a
 * repository does not churn the file.
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
		lines = closeUnterminatedFence(lines);
		lines = ensureTitle(lines);
		lines = canonicalizeHeadings(lines);
		lines = appendMissingSections(lines);
		lines = ensureSectionBodies(lines);
		lines = ensureAgentsReference(lines);
		return join(lines);
	}

	private List<String> splitLines(String content) {
		String normalized = content.replace("\r\n", "\n").replace("\r", "\n");
		return new ArrayList<>(List.of(normalized.split("\n", -1)));
	}

	/**
	 * Closes a fence the document opened and never closed, so everything appended
	 * below it lands outside the block. The closing marker is the one the fence was
	 * opened with, and a document whose fences already balance is returned untouched,
	 * so the reshape stays idempotent.
	 */
	private List<String> closeUnterminatedFence(List<String> lines) {
		String open = scanFences(lines).openAtEnd();
		if (open == null) {
			return lines;
		}
		List<String> result = new ArrayList<>(lines);
		result.add(open);
		return result;
	}

	private List<String> ensureTitle(List<String> lines) {
		if (TITLE.equals(firstNonBlank(lines))) {
			return lines;
		}
		List<String> result = new ArrayList<>();
		result.add(TITLE);
		result.add("");
		result.addAll(lines);
		return result;
	}

	private String firstNonBlank(List<String> lines) {
		return lines.stream().map(String::strip).filter(line -> !line.isEmpty()).findFirst().orElse("");
	}

	private List<String> canonicalizeHeadings(List<String> lines) {
		List<String> result = new ArrayList<>(lines);
		boolean[] fence = fenceMask(result);
		Set<Integer> claimed = reservedHeadings(result, fence);
		for (String required : REQUIRED_SECTIONS) {
			canonicalize(result, fence, required, claimed);
		}
		return result;
	}

	/**
	 * The indices of lines that already are a required heading exactly, reserved so
	 * a near-match search never renames a heading that is already serving another
	 * required section.
	 */
	private Set<Integer> reservedHeadings(List<String> lines, boolean[] fence) {
		return matchesOutsideFences(lines, fence, line -> REQUIRED_SECTIONS.contains(line.strip()))
				.boxed()
				.collect(Collectors.toCollection(LinkedHashSet::new));
	}

	private void canonicalize(List<String> lines, boolean[] fence, String required, Set<Integer> claimed) {
		if (hasHeading(lines, fence, required)) {
			return;
		}
		int index = firstNearMatch(lines, fence, required, claimed);
		if (index >= 0) {
			lines.set(index, required);
			claimed.add(index);
		}
	}

	private int firstNearMatch(List<String> lines, boolean[] fence, String required, Set<Integer> claimed) {
		return matchesOutsideFences(lines, fence, line -> isNearMatch(line, required))
				.filter(index -> !claimed.contains(index))
				.findFirst()
				.orElse(-1);
	}

	private boolean isNearMatch(String line, String required) {
		String stripped = line.strip();
		return isHeading(stripped) && nearMatches(stripped, required);
	}

	/**
	 * A heading is a near-miss of a required one when it is the required heading in
	 * a different case, or the required heading followed by a space and extra words
	 * — the two shapes {@code claude init} actually produces ({@code ## Project
	 * purpose} for {@code ## Project}, {@code ## Principles for Java development}
	 * for {@code ## Principles for Java Development}). The trailing space keeps
	 * {@code ## Maven} from matching an unrelated {@code ## Mavenish} heading.
	 */
	private boolean nearMatches(String heading, String required) {
		String actual = heading.toLowerCase(Locale.ROOT);
		String wanted = required.toLowerCase(Locale.ROOT);
		return actual.equals(wanted) || actual.startsWith(wanted + " ");
	}

	private List<String> appendMissingSections(List<String> lines) {
		boolean[] fence = fenceMask(lines);
		List<String> missing = missingSections(lines, fence);
		if (missing.isEmpty()) {
			return lines;
		}
		List<String> result = new ArrayList<>(lines);
		for (String section : missing) {
			appendSection(result, section);
		}
		return result;
	}

	private List<String> missingSections(List<String> lines, boolean[] fence) {
		List<String> missing = new ArrayList<>();
		for (String required : REQUIRED_SECTIONS) {
			addIfMissing(missing, lines, fence, required);
		}
		return missing;
	}

	private void addIfMissing(List<String> missing, List<String> lines, boolean[] fence, String required) {
		if (!hasHeading(lines, fence, required)) {
			missing.add(required);
		}
	}

	private void appendSection(List<String> lines, String section) {
		lines.add("");
		lines.add(section);
		lines.add("");
		lines.add(STUB_BODY);
	}

	/**
	 * Gives a stub body to every required section that has none. Appended sections
	 * already carry one, so this only ever fills in a heading the generated document
	 * supplied — typically a near-miss renamed in place above whose section the
	 * document left bare.
	 */
	private List<String> ensureSectionBodies(List<String> lines) {
		List<String> result = new ArrayList<>(lines);
		for (String required : REQUIRED_SECTIONS) {
			insertStubBodyIfEmpty(result, required);
		}
		return result;
	}

	/**
	 * The fence mask is rebuilt per section because inserting a stub shifts every
	 * later line, and the sections are few enough for that to stay cheap.
	 */
	private void insertStubBodyIfEmpty(List<String> lines, String required) {
		boolean[] fence = fenceMask(lines);
		int index = indexOfHeading(lines, fence, required);
		if (index >= 0 && !hasBody(lines, fence, index)) {
			lines.add(index + 1, "");
			lines.add(index + 2, STUB_BODY);
		}
	}

	/**
	 * Mirrors how the rule decides a section is non-empty: before the next heading
	 * at its own level or shallower, the section must carry prose, a code block, or
	 * a deeper sub-heading. Blank lines are neither, so the scan looks past them.
	 */
	private boolean hasBody(List<String> lines, boolean[] fence, int headingIndex) {
		int level = headingLevel(lines.get(headingIndex).strip());
		return IntStream.range(headingIndex + 1, lines.size())
				.mapToObj(index -> classifyBodyLine(lines, fence, index, level))
				.flatMap(Optional::stream)
				.findFirst()
				.orElse(false);
	}

	/**
	 * @return whether the line is the section's body ({@code true}) or ends it
	 *         ({@code false}), or empty when it is blank and so decides neither
	 */
	private Optional<Boolean> classifyBodyLine(List<String> lines, boolean[] fence, int index, int level) {
		if (fence[index]) {
			return Optional.of(true);
		}
		String line = lines.get(index).strip();
		if (isHeading(line)) {
			return Optional.of(headingLevel(line) > level);
		}
		return line.isEmpty() ? Optional.empty() : Optional.of(true);
	}

	private int headingLevel(String heading) {
		return (int) heading.chars().takeWhile(character -> character == '#').count();
	}

	private List<String> ensureAgentsReference(List<String> lines) {
		boolean[] fence = fenceMask(lines);
		if (containsOutsideFences(lines, fence, AGENTS_REFERENCE)) {
			return lines;
		}
		List<String> result = new ArrayList<>(lines);
		int titleIndex = indexOfTitle(result, fence);
		result.add(titleIndex + 1, "");
		result.add(titleIndex + 2, AGENTS_REFERENCE_LINE);
		result.add(titleIndex + 3, "");
		return result;
	}

	/**
	 * The title is the first non-blank line by the time this runs, and a fence marker
	 * would itself be a non-blank line before it, so the title can never be one the
	 * mask covers. A document with no title line at all falls back to the top.
	 */
	private int indexOfTitle(List<String> lines, boolean[] fence) {
		return Math.max(indexOfHeading(lines, fence, TITLE), 0);
	}

	private boolean hasHeading(List<String> lines, boolean[] fence, String heading) {
		return indexOfHeading(lines, fence, heading) >= 0;
	}

	/** @return the index of the heading outside code fences, or {@code -1} when absent */
	private int indexOfHeading(List<String> lines, boolean[] fence, String heading) {
		return matchesOutsideFences(lines, fence, line -> line.strip().equals(heading)).findFirst().orElse(-1);
	}

	private boolean containsOutsideFences(List<String> lines, boolean[] fence, String token) {
		return matchesOutsideFences(lines, fence, line -> line.contains(token)).findFirst().isPresent();
	}

	/** The indices of the lines outside code fences whose text matches, in document order. */
	private IntStream matchesOutsideFences(List<String> lines, boolean[] fence, Predicate<String> match) {
		return IntStream.range(0, lines.size()).filter(index -> !fence[index] && match.test(lines.get(index)));
	}

	private boolean isHeading(String stripped) {
		return stripped.startsWith("#");
	}

	private boolean[] fenceMask(List<String> lines) {
		return scanFences(lines).mask();
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

	private Fences scanFences(List<String> lines) {
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
	private String applyFence(String line, String open, boolean[] mask, int index) {
		String marker = fenceMarker(line);
		if (open == null) {
			mask[index] = marker != null;
			return marker;
		}
		mask[index] = true;
		return sameMarker(marker, open) ? null : open;
	}

	private boolean sameMarker(String marker, String open) {
		return marker != null && marker.charAt(0) == open.charAt(0);
	}

	private String fenceMarker(String line) {
		if (line.startsWith(BACKTICK_FENCE)) {
			return BACKTICK_FENCE;
		}
		if (line.startsWith(TILDE_FENCE)) {
			return TILDE_FENCE;
		}
		return null;
	}

	private String join(List<String> lines) {
		return String.join("\n", withoutTrailingBlanks(lines)) + "\n";
	}

	private List<String> withoutTrailingBlanks(List<String> lines) {
		int end = lines.size();
		while (end > 0 && lines.get(end - 1).isBlank()) {
			end--;
		}
		return lines.subList(0, end);
	}
}
