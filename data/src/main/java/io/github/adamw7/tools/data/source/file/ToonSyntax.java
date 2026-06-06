package io.github.adamw7.tools.data.source.file;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Lexical helpers shared by the TOON (Token-Oriented Object Notation) data sources:
 * the line patterns, indentation measurement and value tokenising used to interpret a
 * single line of TOON text.
 */
final class ToonSyntax {

	static final Pattern ARRAY_HEADER_PATTERN = Pattern.compile("^(\\w+)\\[(\\d+)\\](\\{([^}]+)\\})?:\\s*(.*)$");
	static final Pattern KEY_VALUE_PATTERN = Pattern.compile("^(\\w+(?:\\.\\w+)*):\\s*(.*)$");

	private ToonSyntax() {
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
