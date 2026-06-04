package io.github.adamw7.tools.data.source.file;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.regex.Matcher;

/**
 * Streaming counterpart of {@link InMemoryTOONDataSource}. Reads TOON one line at a time and
 * emits flattened {@code {key, value}} rows, keeping only a stack of the enclosing objects
 * and the array block currently being read, so memory use is bounded by nesting depth and
 * the widest single line rather than by document size.
 */
public class StreamingTOONDataSource extends AbstractStreamingFileSource {

	private final Deque<String[]> pending = new ArrayDeque<>();
	private final Deque<ObjectFrame> objectStack = new ArrayDeque<>();
	private BufferedReader reader;
	private Block block;
	private boolean exhausted;

	public StreamingTOONDataSource(String fileName) {
		super(fileName);
	}

	public StreamingTOONDataSource(InputStream inputStream) {
		super(inputStream);
	}

	@Override
	protected void initialise(InputStream stream) throws IOException {
		reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8));
		pending.clear();
		objectStack.clear();
		objectStack.push(new ObjectFrame("", -1));
		block = null;
		exhausted = false;
	}

	@Override
	protected String[] readNextRow() throws IOException {
		String[] row = pending.poll();
		while (row == null && !exhausted) {
			pullLine();
			row = pending.poll();
		}
		return row;
	}

	private void pullLine() throws IOException {
		String line = reader.readLine();
		if (line == null) {
			finishInput();
		} else {
			processLine(line);
		}
	}

	private void finishInput() {
		exhausted = true;
		closeBlock();
	}

	private void processLine(String line) {
		String trimmed = line.trim();
		if (trimmed.isEmpty()) {
			return;
		}
		int indent = ToonSyntax.indentationOf(line);
		if (continuesBlock(indent, trimmed)) {
			return;
		}
		interpret(indent, trimmed);
	}

	private boolean continuesBlock(int indent, String trimmed) {
		if (block == null) {
			return false;
		}
		if (!block.continues(indent, trimmed)) {
			closeBlock();
			return false;
		}
		block.consume(indent, trimmed);
		if (block.isComplete()) {
			closeBlock();
		}
		return true;
	}

	private void closeBlock() {
		if (block != null) {
			block.close();
			block = null;
		}
	}

	private void interpret(int indent, String trimmed) {
		popFramesTo(indent);
		String prefix = objectStack.peek().prefix;

		Matcher arrayMatcher = ToonSyntax.ARRAY_HEADER_PATTERN.matcher(trimmed);
		if (arrayMatcher.matches()) {
			startArray(prefix, indent, arrayMatcher);
			return;
		}

		Matcher kvMatcher = ToonSyntax.KEY_VALUE_PATTERN.matcher(trimmed);
		if (kvMatcher.matches()) {
			handleKeyValue(prefix, indent, kvMatcher);
		}
	}

	private void popFramesTo(int indent) {
		while (objectStack.peek().indent >= indent) {
			objectStack.pop();
		}
	}

	private void handleKeyValue(String prefix, int indent, Matcher matcher) {
		String key = matcher.group(1);
		String value = matcher.group(2);
		if (value.isEmpty()) {
			objectStack.push(new ObjectFrame(prefix + key + ".", indent));
		} else {
			enqueue(prefix + key, ToonSyntax.unquote(value));
		}
	}

	private void startArray(String prefix, int indent, Matcher matcher) {
		String arrayKey = prefix + matcher.group(1);
		int count = Integer.parseInt(matcher.group(2));
		String fields = matcher.group(4);
		String inline = matcher.group(5);

		if (fields != null && !fields.isEmpty()) {
			startTabular(arrayKey, count, indent, fields, inline);
		} else if (!inline.isEmpty()) {
			parseInlineArray(arrayKey, inline);
		} else {
			block = new NestedArrayBlock(arrayKey, count);
		}
	}

	private void startTabular(String arrayKey, int count, int indent, String fieldsText, String inline) {
		String[] fields = splitFields(fieldsText);
		enqueue(arrayKey, String.valueOf(count));
		for (String field : fields) {
			enqueue(field, field);
		}

		TabularBlock tabular = new TabularBlock(arrayKey, fields, count);
		block = tabular;

		if (inline != null && !inline.trim().isEmpty()) {
			tabular.consume(indent, inline.trim());
			if (tabular.isComplete()) {
				closeBlock();
			}
		}
	}

	private String[] splitFields(String fieldsText) {
		String[] raw = fieldsText.split(",");
		String[] trimmed = new String[raw.length];
		for (int i = 0; i < raw.length; i++) {
			trimmed[i] = raw[i].trim();
		}
		return trimmed;
	}

	private void parseInlineArray(String arrayKey, String inline) {
		String[] items = ToonSyntax.splitRow(inline);
		enqueue(arrayKey, String.valueOf(items.length));
		for (int j = 0; j < items.length; j++) {
			enqueue(arrayKey + "[" + j + "]", ToonSyntax.unquote(items[j].trim()));
		}
	}

	private void enqueue(String key, String value) {
		pending.addLast(new String[] { key, value });
	}

	@Override
	protected void releaseResources() throws IOException {
		pending.clear();
		objectStack.clear();
		block = null;
		if (reader != null) {
			reader.close();
		}
	}

	private record ObjectFrame(String prefix, int indent) {
	}

	private abstract static class Block {
		abstract boolean continues(int indent, String trimmed);

		abstract void consume(int indent, String trimmed);

		abstract boolean isComplete();

		void close() {
		}
	}

	private final class TabularBlock extends Block {
		private final String arrayKey;
		private final String[] fields;
		private final int count;
		private int rowIndex;

		private TabularBlock(String arrayKey, String[] fields, int count) {
			this.arrayKey = arrayKey;
			this.fields = fields;
			this.count = count;
		}

		@Override
		boolean continues(int indent, String trimmed) {
			return rowIndex < count && !isNewSection(indent, trimmed);
		}

		private boolean isNewSection(int indent, String trimmed) {
			if (indent != 0) {
				return false;
			}
			return ToonSyntax.KEY_VALUE_PATTERN.matcher(trimmed).matches()
					|| ToonSyntax.ARRAY_HEADER_PATTERN.matcher(trimmed).matches();
		}

		@Override
		void consume(int indent, String trimmed) {
			String[] values = ToonSyntax.splitRow(trimmed);
			int limit = Math.min(fields.length, values.length);
			for (int j = 0; j < limit; j++) {
				enqueue(arrayKey + "[" + rowIndex + "]." + fields[j], ToonSyntax.unquote(values[j].trim()));
			}
			rowIndex++;
		}

		@Override
		boolean isComplete() {
			return rowIndex >= count;
		}
	}

	private final class NestedArrayBlock extends Block {
		private final String arrayKey;
		private final int count;
		private int itemIndex;
		private int baseIndent = -1;

		private NestedArrayBlock(String arrayKey, int count) {
			this.arrayKey = arrayKey;
			this.count = count;
		}

		@Override
		boolean continues(int indent, String trimmed) {
			if (itemIndex >= count) {
				return false;
			}
			return baseIndent <= 0 || indent >= baseIndent;
		}

		@Override
		void consume(int indent, String trimmed) {
			if (baseIndent == -1) {
				baseIndent = indent;
			}
			if (trimmed.startsWith("-")) {
				processItem(trimmed.substring(1).trim());
			}
		}

		private void processItem(String content) {
			if (ToonSyntax.ARRAY_HEADER_PATTERN.matcher(content).matches()) {
				return;
			}
			if (!content.isEmpty()) {
				enqueue(arrayKey + "[" + itemIndex + "]", ToonSyntax.unquote(content));
			}
			itemIndex++;
		}

		@Override
		boolean isComplete() {
			return itemIndex >= count;
		}

		@Override
		void close() {
			enqueue(arrayKey, String.valueOf(count));
		}
	}
}
