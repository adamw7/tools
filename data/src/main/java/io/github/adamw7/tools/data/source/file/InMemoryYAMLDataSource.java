package io.github.adamw7.tools.data.source.file;

import java.io.InputStream;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;

/**
 * In-memory YAML data source. It flattens through
 * {@link AbstractInMemoryJacksonDataSource}, the flattening
 * {@link InMemoryJSONDataSource} uses, so a document written as either syntax
 * yields the same rows — and the ones the streaming
 * {@link IterableYAMLDataSource} emits.
 */
public class InMemoryYAMLDataSource extends AbstractInMemoryJacksonDataSource {

	private static final ObjectMapper MAPPER = new ObjectMapper(new YAMLFactory());

	public InMemoryYAMLDataSource(InputStream inputStream) {
		super(inputStream);
	}

	public InMemoryYAMLDataSource(String filePath) {
		super(filePath);
	}

	@Override
	protected ObjectMapper mapper() {
		return MAPPER;
	}
}
