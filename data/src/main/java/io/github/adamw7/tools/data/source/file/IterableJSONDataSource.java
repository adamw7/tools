package io.github.adamw7.tools.data.source.file;

import java.io.InputStream;

import com.fasterxml.jackson.core.JsonFactory;

/**
 * Iterable counterpart of {@link InMemoryJSONDataSource}. Reads a JSON document token by
 * token and emits flattened {@code {key, value}} rows without holding the whole document in
 * memory.
 */
public class IterableJSONDataSource extends AbstractIterableJacksonDataSource {

	public IterableJSONDataSource(String fileName) {
		super(fileName);
	}

	public IterableJSONDataSource(InputStream inputStream) {
		super(inputStream);
	}

	@Override
	protected JsonFactory createFactory() {
		return new JsonFactory();
	}
}
