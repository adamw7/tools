package io.github.adamw7.context.mcp;

import java.util.Map;

import io.github.adamw7.context.Language;
import io.github.adamw7.context.tree.ProjectTreeBuilder;
import io.github.adamw7.context.tree.ProjectTreeNode;
import io.github.adamw7.tools.mcp.ToolResult;

/**
 * Shared skeleton for the MCP tools that scan a whole project rather than a single
 * class within it. On top of the arguments {@link AbstractContextTool} resolves for
 * every tool, they build the same tree; they differ only in how they render it,
 * which subclasses supply through {@link #render}. Keeping that step here removes
 * the duplication between {@link ProjectTreeTool} and {@link OkfBundleTool} — the
 * whole-project counterpart of {@link AbstractClassContextTool}.
 */
abstract class AbstractProjectScanTool extends AbstractContextTool {

	protected AbstractProjectScanTool(PathPolicy pathPolicy) {
		super(pathPolicy);
	}

	@Override
	protected final ToolResult answer(Scan scan, Map<String, Object> arguments) {
		ProjectTreeNode tree = new ProjectTreeBuilder(scan.language(), scan.depth()).build(scan.root());
		return ToolResult.success(render(tree, scan.language(), arguments));
	}

	/**
	 * Renders the scanned tree in whatever shape the tool returns it. {@code arguments}
	 * is passed on for a tool that reads one of its own beyond the path, language and
	 * depth every scan takes.
	 */
	protected abstract String render(ProjectTreeNode tree, Language language, Map<String, Object> arguments);
}
