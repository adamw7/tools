package io.github.adamw7.tools.data.source.file;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Iterator;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;

/**
 * Streams a Jackson document (JSON or YAML) and flattens it into {@code {key, value}} rows
 * using dotted paths for nested objects and {@code [index]} for array elements
 * (for example {@code people[0].address.city}).
 *
 * <p>The parser is pulled one token at a time and only a stack of the containers currently
 * being descended is retained, so memory use is proportional to nesting depth rather than
 * document size.</p>
 */
public abstract class AbstractStreamingJacksonDataSource extends AbstractStreamingFileSource {

	private final Deque<Frame> frames = new ArrayDeque<>();
	private JsonParser parser;

	protected AbstractStreamingJacksonDataSource(String fileName) {
		super(fileName);
	}

	protected AbstractStreamingJacksonDataSource(InputStream inputStream) {
		super(inputStream);
	}

	/** Supplies the format-specific factory (JSON or YAML). */
	protected abstract JsonFactory createFactory();

	@Override
	protected void initialise(InputStream stream) throws IOException {
		frames.clear();
		parser = createFactory().createParser(stream);
	}

	@Override
	protected String[] readNextRow() throws IOException {
		JsonToken token = parser.nextToken();
		String[] row = null;
		while (token != null && row == null) {
			row = consume(token);
			token = row == null ? parser.nextToken() : token;
		}
		return row;
	}

	private String[] consume(JsonToken token) throws IOException {
		if (token == JsonToken.START_OBJECT) {
			frames.push(Frame.object());
			return null;
		}
		if (token == JsonToken.START_ARRAY) {
			frames.push(Frame.array());
			return null;
		}
		if (token == JsonToken.FIELD_NAME) {
			frames.peek().name = parser.currentName();
			return null;
		}
		if (token == JsonToken.END_OBJECT || token == JsonToken.END_ARRAY) {
			frames.pop();
			advanceArrayIndex();
			return null;
		}
		return scalarRow(token);
	}

	private String[] scalarRow(JsonToken token) throws IOException {
		String[] row = { buildKey(), scalarValue(token) };
		advanceArrayIndex();
		return row;
	}

	private String scalarValue(JsonToken token) throws IOException {
		if (token == JsonToken.VALUE_NULL) {
			return "null";
		}
		return parser.getValueAsString();
	}

	private void advanceArrayIndex() {
		Frame top = frames.peek();
		if (top != null && top.array) {
			top.index++;
		}
	}

	private String buildKey() {
		StringBuilder key = new StringBuilder();
		Iterator<Frame> outerToInner = frames.descendingIterator();
		while (outerToInner.hasNext()) {
			append(key, outerToInner.next());
		}
		return key.toString();
	}

	private void append(StringBuilder key, Frame frame) {
		if (frame.array) {
			key.append('[').append(frame.index).append(']');
		} else {
			appendName(key, frame.name);
		}
	}

	private void appendName(StringBuilder key, String name) {
		if (key.length() > 0) {
			key.append('.');
		}
		key.append(name);
	}

	@Override
	protected void releaseResources() throws IOException {
		frames.clear();
		if (parser != null) {
			parser.close();
		}
	}

	private static final class Frame {
		private final boolean array;
		private String name;
		private int index;

		private Frame(boolean array) {
			this.array = array;
		}

		private static Frame object() {
			return new Frame(false);
		}

		private static Frame array() {
			return new Frame(true);
		}
	}
}
