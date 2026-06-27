package io.github.adamw7.tools.data.source.file;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.regex.Pattern;

/**
 * Lexical and grammar helpers shared by the TOON (Token-Oriented Object Notation) data sources:
 * the line patterns, indentation measurement and value tokenising used to interpret a single
 * line of TOON text, plus the rules that flatten array headers and rows into {@code key/value}
 * pairs. The pairs are handed to a {@link BiConsumer} sink so the in-memory source can collect
 * them in a map while the iterable source enqueues them as rows, without either re-implementing
 * the grammar.
 */
final class ToonSyntax {

	static final Pattern ARRAY_HEADER_PATTERN = Pattern.compile("^(\\w+)\\[(\\d+)\\](\\{([^}]+)\\})?:\\s*(.*)$");
	static final Pattern KEY_VALUE_PATTERN = Pattern.compile("^(\\w+(?:\\.\\w+)*):\\s*(.*)$");

	private ToonSyntax() {
	}

	/**
	 * Whether {@code trimmed} at the given indentation starts a new top-level section, i.e. a
	 * key-value pair or array header at column zero. Used to detect where a tabular array's rows
	 * end and the surrounding document resumes.
	 */
	static boolean isTopLevelSection(int indent, String trimmed) {
		if (indent != 0 || trimmed.isEmpty()) {
			return false;
		}
		return KEY_VALUE_PATTERN.matcher(trimmed).matches()
				|| ARRAY_HEADER_PATTERN.matcher(trimmed).matches();
	}

	/** Splits a comma-separated tabular field list, trimming each field name. */
	static String[] splitFields(String fieldsText) {
		String[] raw = fieldsText.split(",");
		String[] trimmed = new String[raw.length];
		for (int i = 0; i < raw.length; i++) {
			trimmed[i] = raw[i].trim();
		}
		return trimmed;
	}

	/** Emits the header pairs of a tabular array: its declared count, then each field as its own key. */
	static void emitTabularHeader(BiConsumer<String, String> sink, String arrayKey, int count, String[] fields) {
		sink.accept(arrayKey, String.valueOf(count));
		for (String field : fields) {
			sink.accept(field, field);
		}
	}

	/** Emits one tabular row as {@code arrayKey[rowIndex].field = value} pairs, ignoring surplus values. */
	static void emitTabularRow(BiConsumer<String, String> sink, String arrayKey, String[] fields,
			String rowData, int rowIndex) {
		String[] values = splitRow(rowData);
		int limit = Math.min(fields.length, values.length);
		for (int j = 0; j < limit; j++) {
			sink.accept(arrayKey + "[" + rowIndex + "]." + fields[j], unquote(values[j].trim()));
		}
	}

	/** Emits an inline primitive array as its element count followed by each {@code key[j] = value} pair. */
	static void emitInlineArray(BiConsumer<String, String> sink, String key, String values) {
		String[] items = splitRow(values);
		sink.accept(key, String.valueOf(items.length));
		for (int j = 0; j < items.length; j++) {
			sink.accept(key + "[" + j + "]", unquote(items[j].trim()));
		}
	}

	static int indentationOf(String line) {
		int count = 0;
		int i = 0;
		while (i < line.length() && isWhitespace(line.charAt(i))) {
			count += whitespaceValue(line.charAt(i));
			i++;
		}
		return count;
	}

	private static boolean isWhitespace(char c) {
		return c == ' ' || c == '\t';
	}

	private static int whitespaceValue(char c) {
		return c == '\t' ? 2 : 1;
	}

	static String[] splitRow(String row) {
		List<String> values = new ArrayList<>();
		StringBuilder current = new StringBuilder();
		boolean inQuotes = false;

		for (int i = 0; i < row.length(); i++) {
			char c = row.charAt(i);
			boolean isUnescapedQuote = c == '"' && (i == 0 || row.charAt(i - 1) != '\\');

			if (isUnescapedQuote) {
				inQuotes = !inQuotes;
				current.append(c);
			} else if (c == ',' && !inQuotes) {
				values.add(current.toString());
				current = new StringBuilder();
			} else {
				current.append(c);
			}
		}

		if (!current.isEmpty()) {
			values.add(current.toString());
		}

		return values.toArray(new String[0]);
	}

	static String unquote(String value) {
		if (value == null || value.isEmpty()) {
			return value;
		}

		String trimmed = value.trim();
		if (!isQuotedString(trimmed)) {
			return trimmed;
		}

		String unquoted = trimmed.substring(1, trimmed.length() - 1);
		return processEscapeSequences(unquoted);
	}

	private static boolean isQuotedString(String value) {
		return value.startsWith("\"") && value.endsWith("\"") && value.length() >= 2;
	}

	private static String processEscapeSequences(String value) {
		return value
				.replace("\\\"", "\"")
				.replace("\\\\", "\\")
				.replace("\\n", "\n")
				.replace("\\r", "\r")
				.replace("\\t", "\t");
	}
}
