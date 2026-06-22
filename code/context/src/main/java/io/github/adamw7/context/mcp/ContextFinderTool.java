package io.github.adamw7.context.mcp;

import java.util.List;
import java.util.Map;
import java.util.Set;

import org.json.JSONArray;

import io.github.adamw7.context.ClassContainer;
import io.github.adamw7.context.Language;
import io.github.adamw7.context.PackageAwareFinder;
import io.modelcontextprotocol.spec.McpSchema.Tool;

/**
 * MCP tool that resolves the classes a single source file depends on. It loads
 * every source of a Java, Kotlin or Scala project, locates the requested class by
 * its simple name, and runs the package-aware {@link PackageAwareFinder} to a
 * bounded depth so that classes sharing a simple name in different packages are
 * told apart. The dependency class names are returned as a JSON array. An unknown
 * class is reported as an error result rather than an exception so the agent gets
 * a clear, actionable message.
 */
public class ContextFinderTool extends AbstractClassContextTool {

	public ContextFinderTool(PathPolicy pathPolicy) {
		super(pathPolicy);
	}

	private final Tool toolDefinition = Tool.builder("find_context",
			Map.of(
					"type", "object",
					"properties", Map.of(
							"path", Map.of("type", "string",
									"description", "absolute path to the project root directory"),
							"class_name", Map.of("type", "string",
									"description", "simple name of the class to inspect, e.g. Foo or Foo.java"),
							"language", Map.of("type", "string",
									"description", "source language: java (default), kotlin or scala"),
							"depth", Map.of("type", "integer",
									"description", "how many levels of transitive dependencies to resolve (default 1)")),
					"required", List.of("path", "class_name")))
			.description("Find the classes a given class depends on, within a Java, Kotlin or Scala project")
			.build();

	@Override
	public Tool getToolDefinition() {
		return toolDefinition;
	}

	@Override
	protected String result(Set<ClassContainer> containers, ClassContainer target, Language language, int depth) {
		List<String> dependencies = new PackageAwareFinder(containers, language).find(target, depth).stream()
				.map(ClassContainer::className)
				.sorted()
				.toList();
		return new JSONArray(dependencies).toString();
	}
}
