package io.github.adamw7.tools.enforcer.text;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

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
 * <p>
 * A value wrapped in matching quotes yields the text inside them, because that is
 * the value YAML declares and the one Claude Code reads. Quoting is not decoration:
 * a description containing {@code : } has to be quoted to parse at all. Returning
 * the quotes as part of the value made a {@code name: "git-commit"} fail the
 * kebab-case convention it follows, a quoted {@code model} miss its whitelist, and
 * every description count two characters it does not have.
 * <p>
 * A trailing comment is not part of the value. YAML ends a plain scalar at a
 * {@code #} that follows whitespace outside quotes, so {@code name: git-commit # the
 * commit helper} declares {@code git-commit}. Reading the note as part of the value
 * failed a name over the kebab-case convention it follows and made every such
 * description count characters Claude Code never sees. What quotes a scalar is a
 * quote that <em>opens</em> it; a quote anywhere else is an ordinary character, so
 * the apostrophe of {@code description: Claude's helper # a note} does not shelter
 * the note from being dropped.
 * <p>
 * A key declared twice yields its <em>last</em> declaration, which is the one a YAML
 * loader keeps and so the one Claude Code acts on. Reading the first instead
 * validated a value the tool never sees — a second {@code description:} could
 * lengthen a capped one, break a name convention, or collide with another
 * definition, and every check would still be looking at the line above it.
 * {@link #duplicateKeys()} reports the duplication itself, since a key written twice
 * is a mistake whichever value wins.
 */
public final class FrontMatter {

	private static final String DELIMITER = "---";
	private static final char NONE = 0;
	private static final char KEY_VALUE_SEPARATOR = ':';
	private static final char COMMENT_START = '#';
	private static final char ESCAPE = '\\';
	private static final char DOUBLE_QUOTE = '"';
	private static final char SINGLE_QUOTE = '\'';
	private static final String ESCAPED_DOUBLE_QUOTE = "\\\"";
	private static final String ESCAPED_SINGLE_QUOTE = "''";

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
	 * A present key with no value yields an empty string, not an empty optional, a
	 * block scalar yields its folded continuation lines rather than the indicator,
	 * and a quoted scalar yields the text inside its quotes. A key declared more than
	 * once yields its last declaration, the one a YAML loader keeps.
	 */
	public Optional<String> value(String key) {
		int index = indexOfEntry(key);
		if (index < 0) {
			return Optional.empty();
		}
		String declared = withoutComment(valueOf(lines.get(index), key));
		return Optional.of(isBlockScalar(declared) ? folded(index + 1) : unquoted(declared));
	}

	/** The declared keys, in document order, without their trailing colon. */
	public List<String> keys() {
		return lines.stream().map(this::entryKey).flatMap(Optional::stream).toList();
	}

	/**
	 * The keys declared more than once, each named once, in the order they first
	 * appear. Only the last of them takes effect, so the earlier lines say something
	 * the tool never reads — the front matter equivalent of the duplicated JSON key
	 * the configuration rules already reject.
	 */
	public List<String> duplicateKeys() {
		Set<String> seen = new LinkedHashSet<>();
		return keys().stream().filter(key -> !seen.add(key)).distinct().toList();
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

	/** The last line declaring {@code key}, since that is the declaration YAML keeps. */
	private int indexOfEntry(String key) {
		for (int i = lines.size() - 1; i >= 0; i--) {
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
		return lines.subList(from, lines.size()).stream()
				.takeWhile(this::isContinuation)
				.map(String::strip)
				.filter(line -> !line.isEmpty())
				.collect(Collectors.joining(" "));
	}

	private boolean isContinuation(String line) {
		return line.isBlank() || isIndented(line);
	}

	private String valueOf(String line, String key) {
		String stripped = line.strip();
		return stripped.substring((key + KEY_VALUE_SEPARATOR).length()).strip();
	}

	/**
	 * The value with a trailing YAML comment removed. A {@code #} that opens a
	 * comment stands outside quotes and follows whitespace, which is what separates
	 * the note in {@code name: git-commit # the commit helper} from the value in
	 * {@code version: 1.0#2}. Keeping the note made a name break the kebab-case
	 * convention it follows and a description count characters a YAML loader — and
	 * so Claude Code — never reads.
	 */
	private static String withoutComment(String value) {
		int comment = commentStart(value);
		return comment < 0 ? value : value.substring(0, comment).strip();
	}

	/** Where a trailing comment opens, or -1 when the value carries none. */
	private static int commentStart(String value) {
		for (int index = endOfScalar(value); index < value.length(); index++) {
			if (value.charAt(index) == COMMENT_START && startsComment(value, index)) {
				return index;
			}
		}
		return -1;
	}

	/**
	 * Where a comment could begin: past the closing quote of a scalar written as a
	 * quoted one, or at the very start of a plain one. Only a quote that <em>opens</em>
	 * the scalar quotes it — everywhere else a quote is an ordinary character, which
	 * is what {@code description: Claude's helper} is made of. Treating that
	 * apostrophe as the start of a quoted run left the scalar looking unterminated,
	 * so the {@code # note} after it was read as part of the description Claude Code
	 * never sees, and every such value counted characters it does not have.
	 */
	private static int endOfScalar(String value) {
		char quote = openingQuote(value);
		return quote == NONE ? 0 : endOfQuotedScalar(value, quote);
	}

	/** The quote a scalar opens with, or {@link #NONE} when it is a plain one. */
	private static char openingQuote(String value) {
		char first = value.isEmpty() ? NONE : value.charAt(0);
		return first == DOUBLE_QUOTE || first == SINGLE_QUOTE ? first : NONE;
	}

	/**
	 * The index just past the closing quote, or the value's length when the scalar
	 * never closes. A {@code \"} inside a double-quoted scalar and a doubled
	 * {@code ''} inside a single-quoted one are the escapes each style writes, not
	 * the close.
	 */
	private static int endOfQuotedScalar(String value, char quote) {
		int index = 1;
		while (index < value.length() && !closesAt(value, index, quote)) {
			index += isEscapeAt(value, index, quote) ? 2 : 1;
		}
		return index + 1;
	}

	private static boolean closesAt(String value, int index, char quote) {
		return value.charAt(index) == quote && !isEscapeAt(value, index, quote);
	}

	/** True when the character at {@code index} escapes the one after it, in the style {@code quote} uses. */
	private static boolean isEscapeAt(String value, int index, char quote) {
		if (quote == DOUBLE_QUOTE) {
			return value.charAt(index) == ESCAPE;
		}
		return value.charAt(index) == SINGLE_QUOTE && value.startsWith(ESCAPED_SINGLE_QUOTE, index);
	}

	/** A {@code #} opens a comment at the start of the value or after whitespace, never mid-token. */
	private static boolean startsComment(String value, int index) {
		return index == 0 || Character.isWhitespace(value.charAt(index - 1));
	}

	/**
	 * The text inside a scalar's matching quotes, with the escape each quoting style
	 * uses resolved, or the value unchanged when it is not quoted.
	 */
	private static String unquoted(String value) {
		if (isWrapped(value, DOUBLE_QUOTE, ESCAPED_DOUBLE_QUOTE)) {
			return interiorOf(value).replace(ESCAPED_DOUBLE_QUOTE, String.valueOf(DOUBLE_QUOTE));
		}
		if (isWrapped(value, SINGLE_QUOTE, ESCAPED_SINGLE_QUOTE)) {
			return interiorOf(value).replace(ESCAPED_SINGLE_QUOTE, String.valueOf(SINGLE_QUOTE));
		}
		return value;
	}

	/**
	 * True when {@code quote} opens and closes the whole value and appears inside it
	 * only as {@code escaped}. An unescaped inner quote means the outer two are not a
	 * pair — {@code "a" and "b"} opens and closes twice — and stripping them would
	 * hand back text the author never wrote.
	 */
	private static boolean isWrapped(String value, char quote, String escaped) {
		if (value.length() < 2 || value.charAt(0) != quote || value.charAt(value.length() - 1) != quote) {
			return false;
		}
		return interiorOf(value).replace(escaped, "").indexOf(quote) < 0;
	}

	private static String interiorOf(String value) {
		return value.substring(1, value.length() - 1);
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
