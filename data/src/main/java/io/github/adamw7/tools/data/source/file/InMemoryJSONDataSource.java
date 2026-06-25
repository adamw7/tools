package io.github.adamw7.tools.data.source.file;

import java.io.InputStream;
import java.util.Map;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

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
		parseJSON(jsonContent.toString());
	}

	private void parseJSON(String jsonString) {
		JsonNode root = read(jsonString);
		extractFieldNames(root);
		flatten(root);
	}

	private JsonNode read(String jsonString) {
		try {
			return MAPPER.readTree(jsonString);
		} catch (JsonProcessingException e) {
			throw new IllegalStateException("Invalid JSON input", e);
		}
	}

	private void extractFieldNames(JsonNode node) {
		for (Map.Entry<String, JsonNode> field : node.properties()) {
			JsonNode value = field.getValue();
			fieldsMap.put(field.getKey(), asText(value));
			if (value.isObject()) {
				extractFieldNames(value);
			}
		}
	}

	private void flatten(JsonNode node) {
		if (node.isArray()) {
			flattenArray(node);
		} else if (node.isObject()) {
			flattenObject(node);
		}
	}

	private void flattenArray(JsonNode array) {
		for (JsonNode element : array) {
			flatten(element);
		}
	}

	private void flattenObject(JsonNode object) {
		for (Map.Entry<String, JsonNode> field : object.properties()) {
			JsonNode value = field.getValue();
			if (value.isContainerNode()) {
				flatten(value);
			} else {
				fieldsMap.put(field.getKey(), value.asText());
			}
		}
	}

	private String asText(JsonNode value) {
		return value.isContainerNode() ? value.toString() : value.asText();
	}
}
