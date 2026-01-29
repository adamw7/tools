package io.github.adamw7.tools.data.source.file;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import io.github.adamw7.tools.data.source.interfaces.InMemoryDataSource;
import io.github.adamw7.tools.data.source.interfaces.IterableDataSource;

/**
 * Data source for TOON (Token-Oriented Object Notation) format files.
 * TOON is a compact, human-readable format designed to minimize tokens for LLM prompts.
 *
 * Supports:
 * - Key-value pairs (key: value)
 * - Primitive arrays (key[N]: val1,val2,...)
 * - Tabular arrays (key[N]{field1,field2}: row1,row2...)
 * - Nested objects via indentation
 */
public class InMemoryTOONDataSource extends AbstractFileSource implements InMemoryDataSource, IterableDataSource {

	private final Map<String, String> fieldsMap = new HashMap<>();
	private Iterator<String> mapIterator;

	// Pattern for array header: key[N] or key[N]{fields}
	private static final Pattern ARRAY_HEADER_PATTERN = Pattern.compile("^(\\w+)\\[(\\d+)\\](\\{([^}]+)\\})?:\\s*(.*)$");
	// Pattern for key-value pair
	private static final Pattern KEY_VALUE_PATTERN = Pattern.compile("^(\\w+(?:\\.\\w+)*):\\s*(.*)$");

	public InMemoryTOONDataSource(InputStream inputStream) {
		super(inputStream);
		parse();
	}

	public InMemoryTOONDataSource(String filePath) {
		super(filePath);
		parse();
	}

	private void parse() {
		List<String> lines = new ArrayList<>();
		while (scanner.hasNextLine()) {
			lines.add(scanner.nextLine());
		}
		parseTOON(lines);
	}

	private void parseTOON(List<String> lines) {
		int i = 0;
		while (i < lines.size()) {
			String line = lines.get(i);
			String trimmed = line.trim();

			if (trimmed.isEmpty()) {
				i++;
				continue;
			}

			// Check for array header
			Matcher arrayMatcher = ARRAY_HEADER_PATTERN.matcher(trimmed);
			if (arrayMatcher.matches()) {
				String key = arrayMatcher.group(1);
				int count = Integer.parseInt(arrayMatcher.group(2));
				String fields = arrayMatcher.group(4); // may be null
				String inlineValues = arrayMatcher.group(5);

				if (fields != null && !fields.isEmpty()) {
					// Tabular format
					i = parseTabularArray(lines, i, key, count, fields, inlineValues);
				} else if (!inlineValues.isEmpty()) {
					// Inline primitive array
					parseInlineArray(key, inlineValues);
					i++;
				} else {
					// Array of arrays or objects on subsequent lines
					i = parseNestedArray(lines, i, key, count);
				}
				continue;
			}

			// Check for key-value pair
			Matcher kvMatcher = KEY_VALUE_PATTERN.matcher(trimmed);
			if (kvMatcher.matches()) {
				String key = kvMatcher.group(1);
				String value = kvMatcher.group(2);

				if (value.isEmpty()) {
					// Nested object starts on next line
					i = parseNestedObject(lines, i, key);
				} else {
					fieldsMap.put(key, unquote(value));
					i++;
				}
				continue;
			}

			i++;
		}
	}

	private int parseTabularArray(List<String> lines, int startIndex, String arrayKey, int count, String fieldsStr, String inlineValues) {
		String[] fields = fieldsStr.split(",");
		fieldsMap.put(arrayKey, String.valueOf(count));

		for (String field : fields) {
			fieldsMap.put(field.trim(), field.trim());
		}

		int rowIndex = 0;
		int i = startIndex + 1;

		// Check if there are inline values on the header line
		if (inlineValues != null && !inlineValues.trim().isEmpty()) {
			parseTabularRow(arrayKey, fields, inlineValues.trim(), rowIndex++);
		}

		// Parse subsequent rows
		while (i < lines.size() && rowIndex < count) {
			String line = lines.get(i);
			String trimmed = line.trim();

			if (trimmed.isEmpty()) {
				i++;
				continue;
			}

			// Check if we've hit a new key-value or array header (not indented row)
			if (getIndentation(line) == 0 && (KEY_VALUE_PATTERN.matcher(trimmed).matches() || ARRAY_HEADER_PATTERN.matcher(trimmed).matches())) {
				break;
			}

			parseTabularRow(arrayKey, fields, trimmed, rowIndex++);
			i++;
		}

		return i;
	}

	private void parseTabularRow(String arrayKey, String[] fields, String rowData, int rowIndex) {
		String[] values = splitRow(rowData);
		for (int j = 0; j < Math.min(fields.length, values.length); j++) {
			String key = arrayKey + "[" + rowIndex + "]." + fields[j].trim();
			fieldsMap.put(key, unquote(values[j].trim()));
		}
	}

	private void parseInlineArray(String key, String values) {
		String[] items = splitRow(values);
		fieldsMap.put(key, String.valueOf(items.length));
		for (int j = 0; j < items.length; j++) {
			fieldsMap.put(key + "[" + j + "]", unquote(items[j].trim()));
		}
	}

	private int parseNestedArray(List<String> lines, int startIndex, String arrayKey, int count) {
		int i = startIndex + 1;
		int itemIndex = 0;
		int baseIndent = -1;

		while (i < lines.size() && itemIndex < count) {
			String line = lines.get(i);
			String trimmed = line.trim();

			if (trimmed.isEmpty()) {
				i++;
				continue;
			}

			int indent = getIndentation(line);
			if (baseIndent == -1) {
				baseIndent = indent;
			}

			// Check if we've gone back to a lower indentation level
			if (indent < baseIndent && !trimmed.isEmpty()) {
				break;
			}

			// Handle list item marker
			if (trimmed.startsWith("-")) {
				String content = trimmed.substring(1).trim();

				// Check if it's a nested array
				Matcher arrayMatcher = ARRAY_HEADER_PATTERN.matcher(content);
				if (arrayMatcher.matches()) {
					i++;
					continue;
				}

				// Simple value
				if (!content.isEmpty()) {
					fieldsMap.put(arrayKey + "[" + itemIndex + "]", unquote(content));
				}
				itemIndex++;
			}

			i++;
		}

		fieldsMap.put(arrayKey, String.valueOf(count));
		return i;
	}

	private int parseNestedObject(List<String> lines, int startIndex, String parentKey) {
		int i = startIndex + 1;
		int baseIndent = -1;

		while (i < lines.size()) {
			String line = lines.get(i);
			String trimmed = line.trim();

			if (trimmed.isEmpty()) {
				i++;
				continue;
			}

			int indent = getIndentation(line);
			if (baseIndent == -1) {
				baseIndent = indent;
			}

			// Check if we've gone back to a lower indentation level
			if (indent < baseIndent) {
				break;
			}

			Matcher kvMatcher = KEY_VALUE_PATTERN.matcher(trimmed);
			if (kvMatcher.matches()) {
				String key = kvMatcher.group(1);
				String value = kvMatcher.group(2);
				String fullKey = parentKey + "." + key;

				if (value.isEmpty()) {
					// Further nesting
					i = parseNestedObject(lines, i, fullKey);
				} else {
					fieldsMap.put(fullKey, unquote(value));
					i++;
				}
			} else {
				i++;
			}
		}

		return i;
	}

	private int getIndentation(String line) {
		int count = 0;
		for (char c : line.toCharArray()) {
			if (c == ' ') {
				count++;
			} else if (c == '\t') {
				count += 2; // Treat tab as 2 spaces
			} else {
				break;
			}
		}
		return count;
	}

	private String[] splitRow(String row) {
		List<String> values = new ArrayList<>();
		StringBuilder current = new StringBuilder();
		boolean inQuotes = false;

		for (int i = 0; i < row.length(); i++) {
			char c = row.charAt(i);

			if (c == '"' && (i == 0 || row.charAt(i - 1) != '\\')) {
				inQuotes = !inQuotes;
				current.append(c);
			} else if (c == ',' && !inQuotes) {
				values.add(current.toString());
				current = new StringBuilder();
			} else {
				current.append(c);
			}
		}

		if (current.length() > 0) {
			values.add(current.toString());
		}

		return values.toArray(new String[0]);
	}

	private String unquote(String value) {
		if (value == null || value.isEmpty()) {
			return value;
		}

		String trimmed = value.trim();
		if (trimmed.startsWith("\"") && trimmed.endsWith("\"") && trimmed.length() >= 2) {
			String unquoted = trimmed.substring(1, trimmed.length() - 1);
			// Handle escape sequences
			return unquoted
					.replace("\\\"", "\"")
					.replace("\\\\", "\\")
					.replace("\\n", "\n")
					.replace("\\r", "\r")
					.replace("\\t", "\t");
		}
		return trimmed;
	}

	@Override
	public void open() {
		if (opened) {
			throw new IllegalStateException("DataSource is already open");
		}
		mapIterator = fieldsMap.keySet().iterator();
		opened = true;
	}

	@Override
	public String[] nextRow() {
		checkIfOpen();
		if (mapIterator.hasNext()) {
			String key = mapIterator.next();
			String value = fieldsMap.get(key);
			return new String[] { key, value };
		}
		return null;
	}

	@Override
	public boolean hasMoreData() {
		checkIfOpen();
		return mapIterator.hasNext();
	}

	@Override
	public void reset() {
		checkIfOpen();
		mapIterator = fieldsMap.keySet().iterator();
	}

	public Iterator<String[]> iterator() {
		return new Iterator<>() {
			@Override
			public boolean hasNext() {
				return hasMoreData();
			}

			@Override
			public String[] next() {
				return nextRow();
			}
		};
	}

	@Override
	public String[] getColumnNames() {
		return fieldsMap.keySet().toArray(new String[] {});
	}

	@Override
	public List<String[]> readAll() {
		return super.readAll();
	}
}
