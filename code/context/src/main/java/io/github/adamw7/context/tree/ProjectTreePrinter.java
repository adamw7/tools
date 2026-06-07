package io.github.adamw7.context.tree;

/**
 * Renders a {@link ProjectTreeNode} tree as indented text. Directories and
 * files are listed hierarchically and each file's dependencies are printed
 * beneath it, giving a compact, human- and LLM-readable view of the project.
 */
public class ProjectTreePrinter {

	private static final String INDENT = "  ";
	private static final String DIRECTORY_MARKER = "[dir] ";
	private static final String FILE_MARKER = "[file] ";
	private static final String DEPENDENCY_MARKER = "-> ";

	public String print(ProjectTreeNode root) {
		StringBuilder builder = new StringBuilder();
		append(builder, root, "");
		return builder.toString();
	}

	private void append(StringBuilder builder, ProjectTreeNode node, String indent) {
		builder.append(indent).append(marker(node)).append(node.name()).append(System.lineSeparator());
		appendDependencies(builder, node, indent + INDENT);
		node.children().forEach(child -> append(builder, child, indent + INDENT));
	}

	private void appendDependencies(StringBuilder builder, ProjectTreeNode node, String indent) {
		node.dependencies().forEach(dependency ->
				builder.append(indent).append(DEPENDENCY_MARKER).append(dependency)
						.append(System.lineSeparator()));
	}

	private String marker(ProjectTreeNode node) {
		return node.isDirectory() ? DIRECTORY_MARKER : FILE_MARKER;
	}
}
