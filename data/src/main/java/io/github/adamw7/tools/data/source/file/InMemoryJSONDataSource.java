package io.github.adamw7.tools.data.source.file;

import java.io.InputStream;
import java.util.Map;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * In-memory JSON data source that flattens a document into {@link #fieldsMap} as
 * {@code key -> value} pairs keyed by dotted path, using {@code .} for nested
 * object fields and {@code [index]} for array elements (for example
 * {@code people[0].address.city}). This mirrors {@link InMemoryYAMLDataSource}
 * and the streaming {@link IterableJSONDataSource}, so the same document read as
 * JSON, YAML or through either access mode yields the same rows.
 */
public class InMemoryJSONDataSource extends AbstractInMemoryMapDataSource {

	private static final ObjectMapper MAPPER = new ObjectMapper();

	public InMemoryJSONDataSource(InputStream inputStream) {
		super(inputStream);
	}

	public InMemoryJSONDataSource(String filePath) {
		super(filePath);
	}

	@Override
	protected void parse() {
		StringBuilder jsonContent = new StringBuilder();
		while (scanner.hasNextLine()) {
			jsonContent.append(scanner.nextLine());
		}
		flattenValue("", read(jsonContent.toString()));
	}

	private JsonNode read(String jsonString) {
		try {
			return MAPPER.readTree(jsonString);
		} catch (JsonProcessingException e) {
			throw new IllegalStateException("Invalid JSON input", e);
		}
	}

	private void flattenValue(String key, JsonNode value) {
		if (value.isObject()) {
			flattenObject(key, value);
		} else if (value.isArray()) {
			flattenArray(key, value);
		} else {
			fieldsMap.put(key, value.asText());
		}
	}

	private void flattenObject(String prefix, JsonNode object) {
		for (Map.Entry<String, JsonNode> field : object.properties()) {
			String key = prefix.isEmpty() ? field.getKey() : prefix + "." + field.getKey();
			flattenValue(key, field.getValue());
		}
	}

	private void flattenArray(String prefix, JsonNode array) {
		for (int index = 0; index < array.size(); index++) {
			flattenValue(prefix + "[" + index + "]", array.get(index));
		}
	}
}
