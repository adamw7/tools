package io.github.adamw7.context.mcp;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import io.github.adamw7.context.Language;
import io.github.adamw7.context.tree.ProjectTreeBuilder;
import io.github.adamw7.context.tree.ProjectTreeDotSerializer;
import io.github.adamw7.context.tree.ProjectTreeJsonSerializer;
import io.github.adamw7.context.tree.ProjectTreeMarkdownSerializer;
import io.github.adamw7.context.tree.ProjectTreeMermaidSerializer;
import io.github.adamw7.context.tree.ProjectTreeNode;
import io.github.adamw7.context.tree.ProjectTreePrinter;
import io.github.adamw7.context.tree.ProjectTreeSerializer;
import io.github.adamw7.tools.mcp.ToolArguments;
import io.github.adamw7.tools.mcp.ToolDefinition;
import io.github.adamw7.tools.mcp.ToolResult;

/**
 * MCP tool that scans a Java, Kotlin or Scala project into a tree of folders,
 * files and the classes each file depends on, then serialises it for a gen-AI
 * agent. The
 * output format ({@code json}, {@code markdown}, {@code text}, {@code dot} or
 * {@code mermaid}) is chosen by the caller; JSON is the default as it is the most
 * convenient for programmatic consumers.
 */
public class ProjectTreeTool implements ContextTool {

	private static final Logger log = LogManager.getLogger(ProjectTreeTool.class.getName());

	private static final int DEFAULT_DEPTH = 1;
	private static final int MAX_DEPTH = 10;
	private static final String DEFAULT_FORMAT = "json";

	private final PathPolicy pathPolicy;

	public ProjectTreeTool(PathPolicy pathPolicy) {
		this.pathPolicy = pathPolicy;
	}

	private final ToolDefinition toolDefinition = new ToolDefinition("project_tree",
			"Scan a Java, Kotlin or Scala project into a tree of folders, files and class dependencies",
			Map.of(
					"type", "object",
					"properties", Map.of(
							"path", Map.of("type", "string",
									"description", "absolute path to the project root directory"),
							"language", Map.of("type", "string",
									"description", "source language: java (default), kotlin or scala"),
							"depth", Map.of("type", "integer",
									"description", "how many levels of transitive dependencies to resolve (default 1)"),
							"format", Map.of("type", "string",
									"description", "output format: json (default), markdown, text, dot or mermaid")),
					"required", List.of("path")));

	@Override
	public ToolDefinition getToolDefinition() {
		return toolDefinition;
	}

	@Override
	public ToolResult apply(Map<String, Object> arguments) {
		log.info("Calling MCP project_tree tool for {}", arguments);
		String rendered = buildTree(arguments);
		return ToolResult.success(rendered);
	}

	private String buildTree(Map<String, Object> arguments) {
		Path root = pathPolicy.resolve(ToolArguments.requiredString(arguments, "path"));
		Language language = LanguageArguments.optionalLanguage(arguments, "language", Language.JAVA);
		int depth = ToolArguments.optionalBoundedInt(arguments, "depth", DEFAULT_DEPTH, 0, MAX_DEPTH);
		ProjectTreeNode tree = new ProjectTreeBuilder(language, depth).build(root);
		return serializerFor(ToolArguments.optionalString(arguments, "format", DEFAULT_FORMAT)).serialize(tree);
	}

	private ProjectTreeSerializer serializerFor(String format) {
		return switch (format.trim().toLowerCase(java.util.Locale.ROOT)) {
			case "markdown" -> new ProjectTreeMarkdownSerializer();
			case "text" -> new ProjectTreePrinter();
			case "dot" -> new ProjectTreeDotSerializer();
			case "mermaid" -> new ProjectTreeMermaidSerializer();
			default -> new ProjectTreeJsonSerializer();
		};
	}
}
