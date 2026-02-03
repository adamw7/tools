package io.github.adamw7.tools.data.source.file;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
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

	private static final Pattern ARRAY_HEADER_PATTERN = Pattern.compile("^(\\w+)\\[(\\d+)\\](\\{([^}]+)\\})?:\\s*(.*)$");
	private static final Pattern KEY_VALUE_PATTERN = Pattern.compile("^(\\w+(?:\\.\\w+)*):\\s*(.*)$");

	public InMemoryTOONDataSource(InputStream inputStream) {
		super(inputStream);
		scanner = createScanner(inputStream);
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
			i = processLine(lines, i);
		}
	}

	private int processLine(List<String> lines, int index) {
		String line = lines.get(index);
		String trimmed = line.trim();

		if (trimmed.isEmpty()) {
			return index + 1;
		}

		Optional<Integer> arrayResult = tryParseArrayHeader(lines, index, trimmed);
		if (arrayResult.isPresent()) {
			return arrayResult.get();
		}

		Optional<Integer> kvResult = tryParseKeyValue(lines, index, trimmed);
        return kvResult.orElseGet(() -> index + 1);
    }

	private Optional<Integer> tryParseArrayHeader(List<String> lines, int index, String trimmed) {
		Matcher arrayMatcher = ARRAY_HEADER_PATTERN.matcher(trimmed);
		if (!arrayMatcher.matches()) {
			return Optional.empty();
		}

		String key = arrayMatcher.group(1);
		int count = Integer.parseInt(arrayMatcher.group(2));
		String fields = arrayMatcher.group(4);
		String inlineValues = arrayMatcher.group(5);

		return Optional.of(parseArray(lines, index, key, count, fields, inlineValues));
	}

	private Optional<Integer> tryParseKeyValue(List<String> lines, int index, String trimmed) {
		Matcher kvMatcher = KEY_VALUE_PATTERN.matcher(trimmed);
		if (!kvMatcher.matches()) {
			return Optional.empty();
		}

		String key = kvMatcher.group(1);
		String value = kvMatcher.group(2);

		if (value.isEmpty()) {
			return Optional.of(parseNestedObject(lines, index, key));
		}

		fieldsMap.put(key, unquote(value));
		return Optional.of(index + 1);
	}

	private int parseArray(List<String> lines, int index, String key, int count, String fields, String inlineValues) {
		if (fields != null && !fields.isEmpty()) {
			return parseTabularArray(lines, index, key, count, fields, inlineValues);
		}

		if (!inlineValues.isEmpty()) {
			parseInlineArray(key, inlineValues);
			return index + 1;
		}

		return parseNestedArray(lines, index, key, count);
	}

	private int parseTabularArray(List<String> lines, int startIndex, String arrayKey, int count, String fieldsStr, String inlineValues) {
		String[] fields = fieldsStr.split(",");
		fieldsMap.put(arrayKey, String.valueOf(count));

		for (String field : fields) {
			fieldsMap.put(field.trim(), field.trim());
		}

		int rowIndex = 0;
		if (inlineValues != null && !inlineValues.trim().isEmpty()) {
			parseTabularRow(arrayKey, fields, inlineValues.trim(), rowIndex++);
		}

		return parseTabularRows(lines, startIndex + 1, arrayKey, count, rowIndex, fields);
	}

	private int parseTabularRows(List<String> lines, int startIndex, String arrayKey, int count, int initialRowIndex, String[] fields) {
		int i = startIndex;
		int rowIndex = initialRowIndex;

		while (i < lines.size() && rowIndex < count && !isNewSection(lines, i)) {
			String trimmed = lines.get(i).trim();
			if (!trimmed.isEmpty()) {
				parseTabularRow(arrayKey, fields, trimmed, rowIndex++);
			}
			i++;
		}

		return i;
	}

	private boolean isNewSection(List<String> lines, int index) {
		String line = lines.get(index);
		String trimmed = line.trim();

		if (trimmed.isEmpty()) {
			return false;
		}

		boolean isTopLevel = getIndentation(line) == 0;
		boolean isKeyValue = KEY_VALUE_PATTERN.matcher(trimmed).matches();
		boolean isArrayHeader = ARRAY_HEADER_PATTERN.matcher(trimmed).matches();

		return isTopLevel && (isKeyValue || isArrayHeader);
	}

	private void parseTabularRow(String arrayKey, String[] fields, String rowData, int rowIndex) {
		String[] values = splitRow(rowData);
		int limit = Math.min(fields.length, values.length);

		for (int j = 0; j < limit; j++) {
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
		int baseIndent = findBaseIndent(lines, i);

		while (i < lines.size() && itemIndex < count) {
			String line = lines.get(i);
			String trimmed = line.trim();

			if (shouldExitNestedContext(trimmed, getIndentation(line), baseIndent)) {
				fieldsMap.put(arrayKey, String.valueOf(count));
				return i;
			}

			if (trimmed.startsWith("-")) {
				itemIndex = processListItem(arrayKey, trimmed, itemIndex);
			}

			i++;
		}

		fieldsMap.put(arrayKey, String.valueOf(count));
		return i;
	}

	private int findBaseIndent(List<String> lines, int startIndex) {
		for (int i = startIndex; i < lines.size(); i++) {
			String line = lines.get(i);
			if (!line.trim().isEmpty()) {
				return getIndentation(line);
			}
		}
		return 0;
	}

	private boolean shouldExitNestedContext(String trimmed, int currentIndent, int baseIndent) {
		return !trimmed.isEmpty() && baseIndent > 0 && currentIndent < baseIndent;
	}

	private int processListItem(String arrayKey, String trimmed, int itemIndex) {
		String content = trimmed.substring(1).trim();

		if (isArrayHeader(content)) {
			return itemIndex;
		}

		if (!content.isEmpty()) {
			fieldsMap.put(arrayKey + "[" + itemIndex + "]", unquote(content));
		}

		return itemIndex + 1;
	}

	private boolean isArrayHeader(String content) {
		return ARRAY_HEADER_PATTERN.matcher(content).matches();
	}

	private int parseNestedObject(List<String> lines, int startIndex, String parentKey) {
		int i = startIndex + 1;
		int baseIndent = findBaseIndent(lines, i);

		while (i < lines.size()) {
			String line = lines.get(i);
			String trimmed = line.trim();
			int currentIndent = getIndentation(line);

			if (shouldExitNestedObject(trimmed, currentIndent, baseIndent)) {
				return i;
			}

			if (!trimmed.isEmpty()) {
				i = parseNestedKeyValue(lines, i, parentKey, trimmed);
			} else {
				i++;
			}
		}

		return i;
	}

	private boolean shouldExitNestedObject(String trimmed, int currentIndent, int baseIndent) {
		return !trimmed.isEmpty() && baseIndent > 0 && currentIndent < baseIndent;
	}

	private int parseNestedKeyValue(List<String> lines, int index, String parentKey, String trimmed) {
		Matcher kvMatcher = KEY_VALUE_PATTERN.matcher(trimmed);

		if (!kvMatcher.matches()) {
			return index + 1;
		}

		String key = kvMatcher.group(1);
		String value = kvMatcher.group(2);
		String fullKey = parentKey + "." + key;

		if (value.isEmpty()) {
			return parseNestedObject(lines, index, fullKey);
		}

		fieldsMap.put(fullKey, unquote(value));
		return index + 1;
	}

	private int getIndentation(String line) {
		int count = 0;
		int i = 0;

		while (i < line.length() && isWhitespace(line.charAt(i))) {
			count += getWhitespaceValue(line.charAt(i));
			i++;
		}

		return count;
	}

	private boolean isWhitespace(char c) {
		return c == ' ' || c == '\t';
	}

	private int getWhitespaceValue(char c) {
		return c == '\t' ? 2 : 1;
	}

	private String[] splitRow(String row) {
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

	private String unquote(String value) {
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

	private boolean isQuotedString(String value) {
		return value.startsWith("\"") && value.endsWith("\"") && value.length() >= 2;
	}

	private String processEscapeSequences(String value) {
		return value
				.replace("\\\"", "\"")
				.replace("\\\\", "\\")
				.replace("\\n", "\n")
				.replace("\\r", "\r")
				.replace("\\t", "\t");
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
