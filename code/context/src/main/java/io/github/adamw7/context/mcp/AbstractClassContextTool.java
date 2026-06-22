package io.github.adamw7.context.mcp;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import io.github.adamw7.context.ClassContainer;
import io.github.adamw7.context.Language;
import io.github.adamw7.context.ProjectSources;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import io.modelcontextprotocol.spec.McpSchema.TextContent;

/**
 * Shared skeleton for the MCP tools that operate on a single class within a
 * project. They all confine and resolve the project path, load every source of
 * the requested {@link Language}, and locate the target class by its simple name;
 * they differ only in what they compute from that class, which subclasses supply
 * through {@link #result}. Keeping the common steps here removes the duplication
 * between {@link ContextFinderTool} and {@link EstimateTokensTool} and keeps their
 * argument handling, class lookup and result envelopes identical.
 */
abstract class AbstractClassContextTool implements ContextTool {

	protected static final int DEFAULT_DEPTH = 1;
	protected static final int MAX_DEPTH = 10;

	private final Logger log = LogManager.getLogger(getClass());

	protected final PathPolicy pathPolicy;

	protected AbstractClassContextTool(PathPolicy pathPolicy) {
		this.pathPolicy = pathPolicy;
	}

	@Override
	public CallToolResult apply(Map<String, Object> arguments) {
		log.info("Calling MCP {} tool for {}", getToolDefinition().name(), arguments);
		Path root = pathPolicy.resolve(ToolArguments.requiredString(arguments, "path"));
		Language language = ToolArguments.optionalLanguage(arguments, "language", Language.JAVA);
		int depth = ToolArguments.optionalBoundedInt(arguments, "depth", DEFAULT_DEPTH, 0, MAX_DEPTH);
		Set<ClassContainer> containers = Set.copyOf(new ProjectSources(language).load(root).values());

		ClassContainer target = findTarget(containers, arguments, language);
		if (target == null) {
			return error("Class not found: " + ToolArguments.requiredString(arguments, "class_name"));
		}
		return success(result(containers, target, language, depth));
	}

	/** Computes the tool's textual result from the located class within its project. */
	protected abstract String result(Set<ClassContainer> containers, ClassContainer target,
			Language language, int depth);

	private ClassContainer findTarget(Set<ClassContainer> containers, Map<String, Object> arguments,
			Language language) {
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

	private CallToolResult success(String text) {
		return CallToolResult.builder()
				.content(List.of(TextContent.builder(text).build()))
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
