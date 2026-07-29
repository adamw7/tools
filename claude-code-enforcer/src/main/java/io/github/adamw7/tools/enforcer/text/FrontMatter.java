package io.github.adamw7.tools.enforcer.text;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * The YAML front matter block at the top of a Markdown document: the lines
 * between a leading {@code ---} delimiter and the next {@code ---} delimiter.
 * <p>
 * This is a deliberately small reader, not a full YAML parser. It understands
 * the flat {@code key: value} shape that Claude Code skill and sub-agent files
 * use, which is all the rules need. Parsing is shared here so every rule agrees
 * on what counts as front matter, which keys it declares, and each key's value.
 * <p>
 * A key is only recognised where YAML puts one: at the start of a line, with no
 * indentation. An indented line continues the value above it, so reading a key out
 * of one would invent entries an author never declared — the {@code Use this when:}
 * of a wrapped description would be reported as an unknown key.
 * <p>
 * A value written as a block scalar ({@code description: >} or {@code |}, with the
 * text on the indented lines below) is folded back into a single line, since the
 * indicator alone is not the value: reading it literally would leave every such
 * definition claiming the same one-character description and escape any length cap.
 * The fold joins the continuation lines with single spaces, which is all the
 * length, blankness, and uniqueness checks need.
 */
public final class FrontMatter {

	private static final String DELIMITER = "---";
	private static final char KEY_VALUE_SEPARATOR = ':';

	/** A block scalar header: {@code >} or {@code |} with optional indentation and chomping indicators. */
	private static final Pattern BLOCK_SCALAR = Pattern.compile("[>|][0-9+-]*");

	private final List<String> lines;

	private FrontMatter(List<String> lines) {
		this.lines = lines;
	}

	/**
	 * Parses the front matter at the start of {@code content}, or returns empty
	 * when the content does not begin with a closed {@code ---} delimited block.
	 * Claude Code only recognises a block whose opening delimiter is the very
	 * first line, so content that reaches its {@code ---} after blank lines has
	 * no front matter here either. A byte-order mark, if any, must already be
	 * stripped by the caller.
	 */
	public static Optional<FrontMatter> parse(String content) {
		List<String> allLines = content.lines().toList();
		if (allLines.isEmpty() || !allLines.get(0).strip().equals(DELIMITER)) {
			return Optional.empty();
		}
		int end = indexOfDelimiter(allLines, 1);
		if (end < 0) {
			return Optional.empty();
		}
		return Optional.of(new FrontMatter(allLines.subList(1, end)));
	}

	/** True when a {@code key:} entry is present, regardless of its value. */
	public boolean hasKey(String key) {
		return indexOfEntry(key) >= 0;
	}

	/**
	 * The trimmed value declared for {@code key}, or empty when the key is absent.
	 * A present key with no value yields an empty string, not an empty optional, and
	 * a block scalar yields its folded continuation lines rather than the indicator.
	 */
	public Optional<String> value(String key) {
		int index = indexOfEntry(key);
		if (index < 0) {
			return Optional.empty();
		}
		String declared = valueOf(lines.get(index), key);
		return Optional.of(isBlockScalar(declared) ? folded(index + 1) : declared);
	}

	/** The declared keys, in document order, without their trailing colon. */
	public List<String> keys() {
		return lines.stream().map(this::entryKey).flatMap(Optional::stream).toList();
	}

	/**
	 * The key a line declares, or empty when the line is not a {@code key:} entry.
	 * This shares its definition with {@link #hasKey} and {@link #value}, so the
	 * three never disagree about whether a line declares a key: a bare {@code key:}
	 * or a {@code key: value} (or {@code key:\tvalue}) counts, while {@code key:value}
	 * without a separating space, comments, list items, and indented continuation
	 * lines do not.
	 */
	private Optional<String> entryKey(String line) {
		String stripped = line.strip();
		if (isIndented(line) || stripped.startsWith("#") || stripped.startsWith("-")) {
			return Optional.empty();
		}
		int separator = stripped.indexOf(KEY_VALUE_SEPARATOR);
		if (separator <= 0) {
			return Optional.empty();
		}
		String key = stripped.substring(0, separator);
		return isEntryFor(line, key) ? Optional.of(key) : Optional.empty();
	}

	private boolean isEntryFor(String line, String key) {
		if (isIndented(line)) {
			return false;
		}
		String stripped = line.strip();
		return stripped.equals(key + KEY_VALUE_SEPARATOR)
				|| stripped.startsWith(key + KEY_VALUE_SEPARATOR + " ")
				|| stripped.startsWith(key + KEY_VALUE_SEPARATOR + "\t");
	}

	/** True when the line is a continuation of the entry above rather than one of its own. */
	private boolean isIndented(String line) {
		return !line.isEmpty() && Character.isWhitespace(line.charAt(0));
	}

	private int indexOfEntry(String key) {
		for (int i = 0; i < lines.size(); i++) {
			if (isEntryFor(lines.get(i), key)) {
				return i;
			}
		}
		return -1;
	}

	private boolean isBlockScalar(String declared) {
		return BLOCK_SCALAR.matcher(declared).matches();
	}

	/**
	 * The block scalar's continuation lines from {@code from}, joined with single
	 * spaces. The block runs until the next entry at the block's own level, so blank
	 * lines inside it are kept as separators and dropped from the result.
	 */
	private String folded(int from) {
		List<String> content = new ArrayList<>();
		for (int i = from; i < lines.size() && isContinuation(lines.get(i)); i++) {
			addContent(lines.get(i), content);
		}
		return String.join(" ", content);
	}

	private boolean isContinuation(String line) {
		return line.isBlank() || isIndented(line);
	}

	private void addContent(String line, List<String> content) {
		String stripped = line.strip();
		if (!stripped.isEmpty()) {
			content.add(stripped);
		}
	}

	private String valueOf(String line, String key) {
		String stripped = line.strip();
		return stripped.substring((key + KEY_VALUE_SEPARATOR).length()).strip();
	}

	private static int indexOfDelimiter(List<String> lines, int from) {
		for (int i = from; i < lines.size(); i++) {
			if (lines.get(i).strip().equals(DELIMITER)) {
				return i;
			}
		}
		return -1;
	}
}
