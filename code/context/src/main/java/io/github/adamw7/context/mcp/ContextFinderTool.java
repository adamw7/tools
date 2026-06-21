package io.github.adamw7.context.mcp;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.json.JSONArray;

import io.github.adamw7.context.ClassContainer;
import io.github.adamw7.context.Finder;
import io.github.adamw7.context.Language;
import io.github.adamw7.context.ProjectSources;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import io.modelcontextprotocol.spec.McpSchema.TextContent;
import io.modelcontextprotocol.spec.McpSchema.Tool;

/**
 * MCP tool that resolves the classes a single source file depends on. It loads
 * every source of a Java or Kotlin project, locates the requested class by its
 * simple name, and runs the regex-based {@link Finder} to a bounded depth. The
 * dependency class names are returned as a JSON array. An unknown class is
 * reported as an error result rather than an exception so the agent gets a clear,
 * actionable message.
 */
public class ContextFinderTool implements ContextTool {

	private static final Logger log = LogManager.getLogger(ContextFinderTool.class.getName());

	private static final int DEFAULT_DEPTH = 1;
	private static final int MAX_DEPTH = 10;

	private final PathPolicy pathPolicy;

	public ContextFinderTool(PathPolicy pathPolicy) {
		this.pathPolicy = pathPolicy;
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
									"description", "source language: java (default) or kotlin"),
							"depth", Map.of("type", "integer",
									"description", "how many levels of transitive dependencies to resolve (default 1)")),
					"required", List.of("path", "class_name")))
			.description("Find the classes a given class depends on, within a Java or Kotlin project")
			.build();

	@Override
	public Tool getToolDefinition() {
		return toolDefinition;
	}

	@Override
	public CallToolResult apply(Map<String, Object> arguments) {
		log.info("Calling MCP find_context tool for {}", arguments);
		Path root = pathPolicy.resolve(ToolArguments.requiredString(arguments, "path"));
		Language language = ToolArguments.optionalLanguage(arguments, "language", Language.JAVA);
		int depth = ToolArguments.optionalBoundedInt(arguments, "depth", DEFAULT_DEPTH, 0, MAX_DEPTH);
		Set<ClassContainer> containers = Set.copyOf(new ProjectSources(language).load(root).values());

		ClassContainer target = findTarget(containers, arguments, language);
		if (target == null) {
			return error("Class not found: " + ToolArguments.requiredString(arguments, "class_name"));
		}
		return success(dependenciesOf(containers, target, language, depth));
	}

	private ClassContainer findTarget(Set<ClassContainer> containers, Map<String, Object> arguments, Language language) {
		String fileName = fileNameOf(ToolArguments.requiredString(arguments, "class_name"), language);
		return containers.stream()
				.filter(container -> container.className().equals(fileName))
				.findFirst()
				.orElse(null);
	}

	private String fileNameOf(String className, Language language) {
		if (className.endsWith(language.extension())) {
			return className;
		}
		return className + language.extension();
	}

	private String dependenciesOf(Set<ClassContainer> containers, ClassContainer target, Language language, int depth) {
		List<String> dependencies = new Finder(containers, language).find(target, depth).stream()
				.map(ClassContainer::className)
				.sorted()
				.toList();
		return new JSONArray(dependencies).toString();
	}

	private CallToolResult success(String dependenciesJson) {
		return CallToolResult.builder()
				.content(List.of(TextContent.builder(dependenciesJson).build()))
				.isError(false)
				.build();
	}

	private CallToolResult error(String message) {
		return CallToolResult.builder()
				.content(List.of(TextContent.builder(message).build()))
				.isError(true)
				.build();
	}
}
