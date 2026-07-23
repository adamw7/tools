package io.github.adamw7.tools.enforcer.text;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * The YAML front matter block at the top of a Markdown document: the lines
 * between a leading {@code ---} delimiter and the next {@code ---} delimiter.
 * <p>
 * This is a deliberately small reader, not a full YAML parser. It understands
 * the flat {@code key: value} shape that Claude Code skill and sub-agent files
 * use, which is all the rules need. Parsing is shared here so every rule agrees
 * on what counts as front matter, which keys it declares, and each key's value.
 */
public final class FrontMatter {

	private static final String DELIMITER = "---";
	private static final char KEY_VALUE_SEPARATOR = ':';

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
		return lines.stream().anyMatch(line -> isEntryFor(line, key));
	}

	/**
	 * The trimmed value declared for {@code key}, or empty when the key is absent.
	 * A present key with no value yields an empty string, not an empty optional.
	 */
	public Optional<String> value(String key) {
		return lines.stream()
				.filter(line -> isEntryFor(line, key))
				.findFirst()
				.map(line -> valueOf(line, key));
	}

	/** The declared keys, in document order, without their trailing colon. */
	public List<String> keys() {
		List<String> keys = new ArrayList<>();
		for (String line : lines) {
			entryKey(line).ifPresent(keys::add);
		}
		return keys;
	}

	/**
	 * The key a line declares, or empty when the line is not a {@code key:} entry.
	 * This shares its definition with {@link #hasKey} and {@link #value}, so the
	 * three never disagree about whether a line declares a key: a bare {@code key:}
	 * or a {@code key: value} (or {@code key:\tvalue}) counts, while {@code key:value}
	 * without a separating space, comments, and list items do not.
	 */
	private Optional<String> entryKey(String line) {
		String stripped = line.strip();
		if (stripped.startsWith("#") || stripped.startsWith("-")) {
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
		String stripped = line.strip();
		return stripped.equals(key + KEY_VALUE_SEPARATOR)
				|| stripped.startsWith(key + KEY_VALUE_SEPARATOR + " ")
				|| stripped.startsWith(key + KEY_VALUE_SEPARATOR + "\t");
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
