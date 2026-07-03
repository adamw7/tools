package io.github.adamw7.tools.data.source.file;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.function.BiConsumer;
import java.util.regex.Matcher;

/**
 * The single, line-driven TOON grammar. Lines are fed one at a time through {@link #accept}
 * and flattened {@code key/value} pairs are pushed to a {@link BiConsumer} sink; {@link #finish}
 * closes any array block still open at end of input.
 *
 * <p>Only a stack of the enclosing objects and the array block currently being read are held,
 * so a caller that drains the sink between lines (the iterable source) keeps memory bounded by
 * nesting depth, while a caller that collects into a map (the in-memory source) reuses the exact
 * same grammar rather than re-implementing it.</p>
 */
final class ToonFlattener {

	private final BiConsumer<String, String> sink;
	private final Deque<ObjectFrame> objectStack = new ArrayDeque<>();
	private Block block;

	ToonFlattener(BiConsumer<String, String> sink) {
		this.sink = sink;
		objectStack.push(new ObjectFrame("", -1));
	}

	void accept(String line) {
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

	void finish() {
		closeBlock();
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
			ToonSyntax.emitInlineArray(this::enqueue, arrayKey, inline);
		} else {
			block = new NestedArrayBlock(arrayKey, count);
		}
	}

	private void startTabular(String arrayKey, int count, int indent, String fieldsText, String inline) {
		String[] fields = ToonSyntax.splitFields(fieldsText);
		ToonSyntax.emitTabularHeader(this::enqueue, arrayKey, count, fields);

		TabularBlock tabular = new TabularBlock(arrayKey, fields, count);
		block = tabular;

		if (inline != null && !inline.trim().isEmpty()) {
			tabular.consume(indent, inline.trim());
			if (tabular.isComplete()) {
				closeBlock();
			}
		}
	}

	private void enqueue(String key, String value) {
		sink.accept(key, value);
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
			return rowIndex < count && !ToonSyntax.isTopLevelSection(indent, trimmed);
		}

		@Override
		void consume(int indent, String trimmed) {
			ToonSyntax.emitTabularRow(ToonFlattener.this::enqueue, arrayKey, fields, trimmed, rowIndex);
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
