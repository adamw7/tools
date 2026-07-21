package io.github.adamw7.tools.mcp;

import java.util.Map;

/**
 * Reads typed values out of the loosely-typed {@code Map<String, Object>} the MCP
 * runtime hands a tool. Keeping the parsing here lets every MCP server share the
 * same rules for required arguments and defaults rather than repeating the
 * conversions and their error messages. A missing required argument or an
 * out-of-range value is reported as an {@link IllegalArgumentException}, which the
 * shared scaffolding turns into a clean tool error result.
 */
public final class ToolArguments {

	private ToolArguments() {
	}

	public static String requiredString(Map<String, Object> arguments, String key) {
		Object value = arguments.get(key);
		if (value == null) {
			throw new IllegalArgumentException("Missing required argument: " + key);
		}
		return String.valueOf(value);
	}

	public static int requiredInt(Map<String, Object> arguments, String key) {
		Object value = arguments.get(key);
		if (value == null) {
			throw new IllegalArgumentException("Missing required argument: " + key);
		}
		return parseInt(key, value);
	}

	public static String optionalString(Map<String, Object> arguments, String key, String defaultValue) {
		Object value = arguments.get(key);
		return value == null ? defaultValue : String.valueOf(value);
	}

	public static int optionalInt(Map<String, Object> arguments, String key, int defaultValue) {
		Object value = arguments.get(key);
		return value == null ? defaultValue : parseInt(key, value);
	}

	public static int optionalBoundedInt(Map<String, Object> arguments, String key, int defaultValue, int min, int max) {
		int value = optionalInt(arguments, key, defaultValue);
		if (value < min || value > max) {
			throw new IllegalArgumentException(
					"Argument " + key + " must be between " + min + " and " + max + " but was " + value);
		}
		return value;
	}

	private static int parseInt(String key, Object value) {
		try {
			return Integer.parseInt(String.valueOf(value).trim());
		} catch (NumberFormatException e) {
			throw new IllegalArgumentException("Argument " + key + " must be an integer but was " + value, e);
		}
	}
}
