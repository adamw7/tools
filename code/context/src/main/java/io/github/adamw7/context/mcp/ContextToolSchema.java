package io.github.adamw7.context.mcp;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The JSON schemas the context tools advertise. Every one of them takes the same
 * project path, language and depth that {@link AbstractContextTool} reads, and
 * two of them additionally name a class, so the property definitions and their
 * wording live here rather than being spelled out again in each tool — where a
 * description could drift from the argument handling that is already shared.
 */
final class ContextToolSchema {

	private static final Map<String, Object> PATH = property("string",
			"absolute path to the project root directory");
	private static final Map<String, Object> LANGUAGE = property("string",
			"source language: java (default), kotlin or scala");
	private static final Map<String, Object> DEPTH = property("integer",
			"how many levels of transitive dependencies to resolve (default 1)");
	private static final Map<String, Object> CLASS_NAME = property("string",
			"simple name of the class to inspect, e.g. Foo or Foo.java");

	private ContextToolSchema() {
	}

	/** The schema of a tool that scans a whole project, plus whatever else it takes. */
	static Map<String, Object> project(Map<String, Object> ownProperties) {
		return schema(ownProperties, List.of("path"));
	}

	/** The schema of a tool that operates on one class within a project. */
	static Map<String, Object> singleClass() {
		return schema(Map.of("class_name", CLASS_NAME), List.of("path", "class_name"));
	}

	static Map<String, Object> property(String type, String description) {
		return Map.of("type", type, "description", description);
	}

	private static Map<String, Object> schema(Map<String, Object> ownProperties, List<String> required) {
		Map<String, Object> properties = new LinkedHashMap<>();
		properties.put("path", PATH);
		properties.putAll(ownProperties);
		properties.put("language", LANGUAGE);
		properties.put("depth", DEPTH);
		return Map.of("type", "object", "properties", Map.copyOf(properties), "required", required);
	}
}
