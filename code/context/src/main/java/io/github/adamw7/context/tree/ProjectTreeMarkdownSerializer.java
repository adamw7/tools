package io.github.adamw7.context.tree;

/**
 * Renders a {@link ProjectTreeNode} tree as a nested Markdown bullet list.
 * Directories and files become list items; each file's dependencies are listed
 * as indented child bullets. The result is a compact, Markdown-aware view well
 * suited to documents and chat-based gen-AI agents.
 */
public class ProjectTreeMarkdownSerializer extends AbstractProjectTreeIndentedSerializer {

	private static final String BULLET = "- ";
	private static final String DIRECTORY_MARKER = "**";
	private static final String DEPENDENCY_MARKER = "depends on: ";

	@Override
	protected String line(ProjectTreeNode node) {
		if (node.isDirectory()) {
			return BULLET + DIRECTORY_MARKER + node.name() + DIRECTORY_MARKER;
		}
		return BULLET + code(node.name());
	}

	@Override
	protected String dependencyLine(String dependency) {
		return BULLET + DEPENDENCY_MARKER + code(dependency);
	}

	private String code(String text) {
		return "`" + text + "`";
	}
}
