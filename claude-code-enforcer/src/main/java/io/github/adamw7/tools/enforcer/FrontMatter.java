package io.github.adamw7.tools.enforcer;

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
final class FrontMatter {

	private static final String DELIMITER = "---";
	private static final char KEY_VALUE_SEPARATOR = ':';

	private final List<String> lines;

	private FrontMatter(List<String> lines) {
		this.lines = lines;
	}

	/**
	 * Parses the front matter at the start of {@code content}, or returns empty
	 * when the content does not begin with a closed {@code ---} delimited block.
	 * A byte-order mark, if any, must already be stripped by the caller.
	 */
	static Optional<FrontMatter> parse(String content) {
		List<String> allLines = content.lines().toList();
		if (!MarkdownText.firstNonBlankLine(content).equals(DELIMITER)) {
			return Optional.empty();
		}
		int start = indexOfDelimiter(allLines, 0);
		int end = indexOfDelimiter(allLines, start + 1);
		if (end < 0) {
			return Optional.empty();
		}
		return Optional.of(new FrontMatter(allLines.subList(start + 1, end)));
	}

	/** True when a {@code key:} entry is present, regardless of its value. */
	boolean hasKey(String key) {
		return lines.stream().anyMatch(line -> isEntryFor(line, key));
	}

	/**
	 * The trimmed value declared for {@code key}, or empty when the key is absent.
	 * A present key with no value yields an empty string, not an empty optional.
	 */
	Optional<String> value(String key) {
		return lines.stream()
				.filter(line -> isEntryFor(line, key))
				.findFirst()
				.map(line -> valueOf(line, key));
	}

	/** The declared keys, in document order, without their trailing colon. */
	List<String> keys() {
		List<String> keys = new ArrayList<>();
		for (String line : lines) {
			addKey(line, keys);
		}
		return keys;
	}

	private void addKey(String line, List<String> keys) {
		String stripped = line.strip();
		int separator = stripped.indexOf(KEY_VALUE_SEPARATOR);
		if (isKeyLine(stripped, separator)) {
			keys.add(stripped.substring(0, separator).strip());
		}
	}

	/** A key line is an unindented {@code key: ...} entry, not a nested value or a comment. */
	private boolean isKeyLine(String stripped, int separator) {
		return separator > 0 && !stripped.startsWith("#") && !stripped.startsWith("-");
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
