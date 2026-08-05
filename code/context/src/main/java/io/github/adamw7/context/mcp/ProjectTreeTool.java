package io.github.adamw7.context.mcp;

import java.util.Locale;
import java.util.Map;

import io.github.adamw7.context.Language;
import io.github.adamw7.context.tree.ProjectTreeDotSerializer;
import io.github.adamw7.context.tree.ProjectTreeJsonSerializer;
import io.github.adamw7.context.tree.ProjectTreeMarkdownSerializer;
import io.github.adamw7.context.tree.ProjectTreeMermaidSerializer;
import io.github.adamw7.context.tree.ProjectTreeNode;
import io.github.adamw7.context.tree.ProjectTreePrinter;
import io.github.adamw7.context.tree.ProjectTreeSerializer;
import io.github.adamw7.tools.mcp.ToolArguments;
import io.github.adamw7.tools.mcp.ToolDefinition;

/**
 * MCP tool that scans a Java, Kotlin or Scala project into a tree of folders,
 * files and the classes each file depends on, then serialises it for a gen-AI
 * agent. The
 * output format ({@code json}, {@code markdown}, {@code text}, {@code dot} or
 * {@code mermaid}) is chosen by the caller; JSON is the default as it is the most
 * convenient for programmatic consumers.
 */
public class ProjectTreeTool extends AbstractProjectScanTool {

	private static final String DEFAULT_FORMAT = "json";

	public ProjectTreeTool(PathPolicy pathPolicy) {
		super(pathPolicy);
	}

	private final ToolDefinition toolDefinition = new ToolDefinition("project_tree",
			"Scan a Java, Kotlin or Scala project into a tree of folders, files and class dependencies",
			ContextToolSchema.project(Map.of("format", ContextToolSchema.property("string",
					"output format: json (default), markdown, text, dot or mermaid"))));

	@Override
	public ToolDefinition getToolDefinition() {
		return toolDefinition;
	}

	@Override
	protected String render(ProjectTreeNode tree, Language language, Map<String, Object> arguments) {
		return serializerFor(ToolArguments.optionalString(arguments, "format", DEFAULT_FORMAT)).serialize(tree);
	}

	private ProjectTreeSerializer serializerFor(String format) {
		return switch (format.trim().toLowerCase(Locale.ROOT)) {
			case "markdown" -> new ProjectTreeMarkdownSerializer();
			case "text" -> new ProjectTreePrinter();
			case "dot" -> new ProjectTreeDotSerializer();
			case "mermaid" -> new ProjectTreeMermaidSerializer();
			default -> new ProjectTreeJsonSerializer();
		};
	}
}
