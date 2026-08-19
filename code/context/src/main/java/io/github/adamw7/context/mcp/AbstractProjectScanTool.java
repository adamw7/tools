package io.github.adamw7.context.mcp;

import java.util.Map;

import io.github.adamw7.context.ContextFactory;
import io.github.adamw7.context.Language;
import io.github.adamw7.context.PackageAwareFinder;
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
 *
 * <p>The tree is resolved with the same package-aware finder {@link ContextFinderTool}
 * uses, so this server answers one question one way: the name-based finder the tree
 * builder defaults to cannot tell two classes sharing a simple name apart, which had
 * the whole-project tools drawing an edge to the wrong file — and, where the wrong
 * file was the one being scanned, a self-edge no class actually has.
 */
abstract class AbstractProjectScanTool extends AbstractContextTool {

	protected AbstractProjectScanTool(PathPolicy pathPolicy) {
		super(pathPolicy);
	}

	@Override
	protected final ToolResult answer(Scan scan, Map<String, Object> arguments) {
		ProjectTreeNode tree = new ProjectTreeBuilder(packageAwareFinder(scan.language()), scan.language(),
				scan.depth()).build(scan.root());
		return ToolResult.success(render(tree, scan.language(), arguments));
	}

	/**
	 * Renders the scanned tree in whatever shape the tool returns it. {@code arguments}
	 * is passed on for a tool that reads one of its own beyond the path, language and
	 * depth every scan takes.
	 */
	protected abstract String render(ProjectTreeNode tree, Language language, Map<String, Object> arguments);

	private ContextFactory packageAwareFinder(Language language) {
		return containers -> new PackageAwareFinder(containers, language);
	}
}
