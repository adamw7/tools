package io.github.adamw7.tools.data.source.file;

import java.io.InputStream;

import com.fasterxml.jackson.core.JsonFactory;

/**
 * Streaming counterpart of {@link InMemoryJSONDataSource}. Reads a JSON document token by
 * token and emits flattened {@code {key, value}} rows without holding the whole document in
 * memory.
 */
public class StreamingJSONDataSource extends AbstractStreamingJacksonDataSource {

	public StreamingJSONDataSource(String fileName) {
		super(fileName);
	}

	public StreamingJSONDataSource(InputStream inputStream) {
		super(inputStream);
	}

	@Override
	protected JsonFactory createFactory() {
		return new JsonFactory();
	}
}
