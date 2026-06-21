package io.github.adamw7.context.tree;

/**
 * Renders a {@link ProjectTreeNode} tree as a nested Markdown bullet list.
 * Directories and files become list items; each file's dependencies are listed
 * as indented child bullets. The result is a compact, Markdown-aware view well
 * suited to documents and chat-based gen-AI agents.
 */
public class ProjectTreeMarkdownSerializer implements ProjectTreeSerializer {

	private static final String INDENT = "  ";
	private static final String BULLET = "- ";
	private static final String DIRECTORY_MARKER = "**";
	private static final String DEPENDENCY_MARKER = "depends on: ";

	@Override
	public String serialize(ProjectTreeNode root) {
		StringBuilder builder = new StringBuilder();
		append(builder, root, "");
		return builder.toString();
	}

	private void append(StringBuilder builder, ProjectTreeNode node, String indent) {
		builder.append(indent).append(BULLET).append(label(node)).append(System.lineSeparator());
		appendDependencies(builder, node, indent + INDENT);
		node.children().forEach(child -> append(builder, child, indent + INDENT));
	}

	private void appendDependencies(StringBuilder builder, ProjectTreeNode node, String indent) {
		node.dependencies().forEach(dependency ->
				builder.append(indent).append(BULLET).append(DEPENDENCY_MARKER).append('`')
						.append(dependency).append('`').append(System.lineSeparator()));
	}

	private String label(ProjectTreeNode node) {
		if (node.isDirectory()) {
			return DIRECTORY_MARKER + node.name() + DIRECTORY_MARKER;
		}
		return "`" + node.name() + "`";
	}
}
