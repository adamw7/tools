package io.github.adamw7.tools.enforcer.text;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * A parsed Markdown document: its lines plus a mask marking which lines belong
 * to a fenced code block. Parsing the fence mask once, at construction, lets the
 * structural checks share it instead of each rebuilding it.
 * <p>
 * Headings are recognised on whole lines outside fenced code blocks, so a
 * heading mentioned inside a {@code ```} or {@code ~~~} fence or in prose is not
 * treated as document structure. The fence mask includes the opening and closing
 * delimiters themselves, so heading detection and body detection agree on what is
 * code and what is structure. A fence opened with one delimiter is only closed by
 * the same delimiter, so a {@code ~~~} line inside a {@code ```} block stays code.
 */
public final class MarkdownDocument {

	private static final String BACKTICK_FENCE = "```";
	private static final String TILDE_FENCE = "~~~";
	private static final String HEADING_PREFIX = "#";
	private static final char HEADING_CHAR = '#';

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
			addIfRequiredHeading(lines.get(i).strip(), insideFence[i], required, ordered);
		}
		return ordered;
	}

	private void addIfRequiredHeading(String line, boolean lineInsideFence, Set<String> required, List<String> ordered) {
		if (!lineInsideFence && required.remove(line)) {
			ordered.add(line);
		}
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
		return line.startsWith(HEADING_PREFIX);
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
		String openMarker = null;
		for (int i = 0; i < lines.size(); i++) {
			openMarker = applyFence(lines.get(i).strip(), openMarker, mask, i);
		}
		return mask;
	}

	/**
	 * Marks line {@code index} as code or structure and returns the fence marker
	 * still open afterwards. A fence is only closed by the marker it was opened
	 * with, so a {@code ~~~} line inside a {@code ```} block, or the reverse, stays
	 * content rather than ending the block.
	 */
	private static String applyFence(String line, String openMarker, boolean[] mask, int index) {
		String marker = fenceMarker(line);
		if (openMarker == null) {
			mask[index] = marker != null;
			return marker;
		}
		mask[index] = true;
		return closesFence(marker, openMarker) ? null : openMarker;
	}

	private static boolean closesFence(String marker, String openMarker) {
		return marker != null && marker.charAt(0) == openMarker.charAt(0);
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
}
