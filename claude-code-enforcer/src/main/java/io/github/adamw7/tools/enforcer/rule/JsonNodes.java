package io.github.adamw7.tools.enforcer.rule;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * Null-safe accessors over Jackson {@link JsonNode} that mirror the optional
 * lookups the JSON enforcer rules rely on. They keep the rule logic free of
 * repeated null and type checks: a lookup that does not resolve to the expected
 * node type yields {@code null} (or a supplied default) rather than throwing.
 */
public final class JsonNodes {

	private JsonNodes() {
	}

	/** The child object at {@code key}, or null when it is absent or not an object. */
	public static JsonNode objectAt(JsonNode node, String key) {
		JsonNode child = node.get(key);
		return child != null && child.isObject() ? child : null;
	}

	/** The child array at {@code key}, or null when it is absent or not an array. */
	public static JsonNode arrayAt(JsonNode node, String key) {
		JsonNode child = node.get(key);
		return child != null && child.isArray() ? child : null;
	}

	/** The array element at {@code index}, or null when it is not an object. */
	public static JsonNode objectAt(JsonNode array, int index) {
		JsonNode element = array.get(index);
		return element != null && element.isObject() ? element : null;
	}

	/** The text at {@code key}, or {@code defaultValue} when it is absent or null. */
	public static String textAt(JsonNode node, String key, String defaultValue) {
		JsonNode child = node.get(key);
		return child != null && !child.isNull() ? child.asText() : defaultValue;
	}

	/** The field names of an object node, preserving document order. */
	public static List<String> fieldNames(JsonNode node) {
		List<String> names = new ArrayList<>();
		for (Map.Entry<String, JsonNode> field : node.properties()) {
			names.add(field.getKey());
		}
		return names;
	}
}
