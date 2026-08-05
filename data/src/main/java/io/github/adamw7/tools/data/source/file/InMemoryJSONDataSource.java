package io.github.adamw7.tools.data.source.file;

import java.io.InputStream;

import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * In-memory JSON data source. The flattening into {@link #fieldsMap} is
 * {@link AbstractInMemoryJacksonDataSource}'s, shared with
 * {@link InMemoryYAMLDataSource} and matching the streaming
 * {@link IterableJSONDataSource}, so the same document read as JSON, YAML or
 * through either access mode yields the same rows.
 */
public class InMemoryJSONDataSource extends AbstractInMemoryJacksonDataSource {

	private static final ObjectMapper MAPPER = new ObjectMapper();

	public InMemoryJSONDataSource(InputStream inputStream) {
		super(inputStream);
	}

	public InMemoryJSONDataSource(String filePath) {
		super(filePath);
	}

	@Override
	protected ObjectMapper mapper() {
		return MAPPER;
	}
}
