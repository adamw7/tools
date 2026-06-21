package io.github.adamw7.context.mcp;

import java.util.Map;

import io.github.adamw7.context.Language;

/**
 * Reads typed values out of the loosely-typed {@code Map<String, Object>} the MCP
 * runtime hands a tool. Keeping the parsing here lets every tool share the same
 * rules for required arguments, defaults, and language resolution rather than
 * repeating the conversions.
 */
final class ToolArguments {

	private ToolArguments() {
	}

	static String requiredString(Map<String, Object> arguments, String key) {
		Object value = arguments.get(key);
		if (value == null) {
			throw new IllegalArgumentException("Missing required argument: " + key);
		}
		return String.valueOf(value);
	}

	static String optionalString(Map<String, Object> arguments, String key, String defaultValue) {
		Object value = arguments.get(key);
		return value == null ? defaultValue : String.valueOf(value);
	}

	static int optionalInt(Map<String, Object> arguments, String key, int defaultValue) {
		Object value = arguments.get(key);
		return value == null ? defaultValue : Integer.parseInt(String.valueOf(value).trim());
	}

	static int optionalBoundedInt(Map<String, Object> arguments, String key, int defaultValue, int min, int max) {
		int value = optionalInt(arguments, key, defaultValue);
		if (value < min || value > max) {
			throw new IllegalArgumentException(
					"Argument " + key + " must be between " + min + " and " + max + " but was " + value);
		}
		return value;
	}

	static Language optionalLanguage(Map<String, Object> arguments, String key, Language defaultLanguage) {
		Object value = arguments.get(key);
		return value == null ? defaultLanguage : Language.fromName(String.valueOf(value));
	}
}
