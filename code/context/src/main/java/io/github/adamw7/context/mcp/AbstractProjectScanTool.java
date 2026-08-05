package io.github.adamw7.context.mcp;

import java.nio.file.Path;
import java.util.Map;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import io.github.adamw7.context.Language;
import io.github.adamw7.context.tree.ProjectTreeBuilder;
import io.github.adamw7.context.tree.ProjectTreeNode;
import io.github.adamw7.tools.mcp.ToolArguments;
import io.github.adamw7.tools.mcp.ToolResult;

/**
 * Shared skeleton for the MCP tools that scan a whole project rather than a single
 * class within it. They all confine and resolve the project path, read the optional
 * language and depth, and build the same tree; they differ only in how they render
 * it, which subclasses supply through {@link #render}. Keeping the common steps here
 * removes the duplication between {@link ProjectTreeTool} and {@link OkfBundleTool}
 * and keeps their argument handling identical — the whole-project counterpart of
 * {@link AbstractClassContextTool}.
 */
abstract class AbstractProjectScanTool implements ContextTool {

	private static final Logger log = LogManager.getLogger(AbstractProjectScanTool.class);

	private static final int DEFAULT_DEPTH = 1;
	private static final int MAX_DEPTH = 10;

	private final PathPolicy pathPolicy;

	protected AbstractProjectScanTool(PathPolicy pathPolicy) {
		this.pathPolicy = pathPolicy;
	}

	@Override
	public final ToolResult apply(Map<String, Object> arguments) {
		log.info("Calling MCP {} tool for {}", getToolDefinition().name(), arguments);
		Path root = pathPolicy.resolve(ToolArguments.requiredString(arguments, "path"));
		Language language = LanguageArguments.optionalLanguage(arguments, "language", Language.JAVA);
		int depth = ToolArguments.optionalBoundedInt(arguments, "depth", DEFAULT_DEPTH, 0, MAX_DEPTH);
		return ToolResult.success(render(new ProjectTreeBuilder(language, depth).build(root), language, arguments));
	}

	/**
	 * Renders the scanned tree in whatever shape the tool returns it. {@code arguments}
	 * is passed on for a tool that reads one of its own beyond the path, language and
	 * depth every scan takes.
	 */
	protected abstract String render(ProjectTreeNode tree, Language language, Map<String, Object> arguments);
}
