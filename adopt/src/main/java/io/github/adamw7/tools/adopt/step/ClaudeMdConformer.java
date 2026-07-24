package io.github.adamw7.tools.adopt.step;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

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
 * near-miss is appended as a fresh stub. Headings inside fenced code blocks are
 * left alone, mirroring how the rule matches, so a {@code ##} line in a code
 * sample is never mistaken for document structure.
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
		lines = ensureTitle(lines);
		lines = canonicalizeHeadings(lines);
		lines = appendMissingSections(lines);
		lines = ensureAgentsReference(lines);
		return join(lines);
	}

	private List<String> splitLines(String content) {
		String normalized = content.replace("\r\n", "\n").replace("\r", "\n");
		return new ArrayList<>(List.of(normalized.split("\n", -1)));
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
		Set<Integer> reserved = new LinkedHashSet<>();
		for (int index = 0; index < lines.size(); index++) {
			addIfReserved(reserved, lines, fence, index);
		}
		return reserved;
	}

	private void addIfReserved(Set<Integer> reserved, List<String> lines, boolean[] fence, int index) {
		if (!fence[index] && REQUIRED_SECTIONS.contains(lines.get(index).strip())) {
			reserved.add(index);
		}
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
		for (int index = 0; index < lines.size(); index++) {
			if (isNearMatch(lines, fence, claimed, index, required)) {
				return index;
			}
		}
		return -1;
	}

	private boolean isNearMatch(List<String> lines, boolean[] fence, Set<Integer> claimed, int index, String required) {
		if (fence[index] || claimed.contains(index)) {
			return false;
		}
		String stripped = lines.get(index).strip();
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

	private List<String> ensureAgentsReference(List<String> lines) {
		boolean[] fence = fenceMask(lines);
		if (containsOutsideFences(lines, fence, AGENTS_REFERENCE)) {
			return lines;
		}
		List<String> result = new ArrayList<>(lines);
		int titleIndex = indexOfTitle(result);
		result.add(titleIndex + 1, "");
		result.add(titleIndex + 2, AGENTS_REFERENCE_LINE);
		result.add(titleIndex + 3, "");
		return result;
	}

	private int indexOfTitle(List<String> lines) {
		for (int index = 0; index < lines.size(); index++) {
			if (lines.get(index).strip().equals(TITLE)) {
				return index;
			}
		}
		return 0;
	}

	private boolean hasHeading(List<String> lines, boolean[] fence, String heading) {
		for (int index = 0; index < lines.size(); index++) {
			if (!fence[index] && lines.get(index).strip().equals(heading)) {
				return true;
			}
		}
		return false;
	}

	private boolean containsOutsideFences(List<String> lines, boolean[] fence, String token) {
		for (int index = 0; index < lines.size(); index++) {
			if (!fence[index] && lines.get(index).contains(token)) {
				return true;
			}
		}
		return false;
	}

	private boolean isHeading(String stripped) {
		return stripped.startsWith("#");
	}

	private boolean[] fenceMask(List<String> lines) {
		boolean[] mask = new boolean[lines.size()];
		String open = null;
		for (int index = 0; index < lines.size(); index++) {
			open = applyFence(lines.get(index).strip(), open, mask, index);
		}
		return mask;
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
