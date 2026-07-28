package io.github.adamw7.tools.enforcer.rule;

import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.StreamReadFeature;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;

/**
 * Null-safe accessors over Jackson {@link JsonNode} that mirror the optional
 * lookups the JSON enforcer rules rely on. They keep the rule logic free of
 * repeated null and type checks: a lookup that does not resolve to the expected
 * node type yields {@code null} (or a supplied default) rather than throwing.
 */
public final class JsonNodes {

	/**
	 * Parses strictly, because these rules exist to catch a hand-edited
	 * configuration file that Claude Code will not read the way its author meant.
	 * Jackson's defaults stop at the first complete value and let a later
	 * definition of a key silently win, so content after the closing brace, and a
	 * duplicated key, would both parse clean; both are rejected here instead.
	 */
	private static final ObjectMapper MAPPER = JsonMapper.builder()
			.enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
			.enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
			.build();

	private JsonNodes() {
	}

	/**
	 * Parses {@code content} into a JSON object node. When it is not valid JSON or
	 * not a JSON object, a violation naming {@code description} (e.g.
	 * {@code settings.json}) is added and {@code null} is returned, so a parse
	 * failure is reported alongside structural problems rather than thrown.
	 */
	public static JsonNode parseObject(String content, String description, List<String> violations) {
		try {
			JsonNode root = MAPPER.readTree(content);
			if (root == null || !root.isObject()) {
				violations.add(description + " is not valid JSON: expected a JSON object");
				return null;
			}
			return root;
		} catch (JsonProcessingException e) {
			violations.add(description + " is not valid JSON: " + e.getOriginalMessage());
			return null;
		}
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
		return node.propertyStream().map(Map.Entry::getKey).toList();
	}
}
