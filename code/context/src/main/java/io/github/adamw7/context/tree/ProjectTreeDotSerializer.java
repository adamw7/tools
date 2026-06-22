package io.github.adamw7.context.tree;

/**
 * Renders the dependency graph of a {@link ProjectTreeNode} tree as a Graphviz
 * <a href="https://graphviz.org/doc/info/lang.html">DOT</a> digraph: every file
 * becomes a node and every dependency it carries becomes a directed edge from the
 * file to the depended-on class. Directories contribute structure but are not
 * drawn, so the result is a focused, render-ready view of how the project's
 * classes depend on one another — complementary to the tree-shaped text, Markdown
 * and JSON serializers.
 */
public class ProjectTreeDotSerializer implements ProjectTreeSerializer {

	private static final String INDENT = "  ";

	@Override
	public String serialize(ProjectTreeNode root) {
		StringBuilder builder = new StringBuilder();
		builder.append("digraph project {").append(System.lineSeparator());
		appendEdges(builder, root);
		builder.append("}").append(System.lineSeparator());
		return builder.toString();
	}

	private void appendEdges(StringBuilder builder, ProjectTreeNode node) {
		appendNodeEdges(builder, node);
		node.children().forEach(child -> appendEdges(builder, child));
	}

	private void appendNodeEdges(StringBuilder builder, ProjectTreeNode node) {
		if (node.isDirectory()) {
			return;
		}
		node.dependencies().forEach(dependency -> appendEdge(builder, node.name(), dependency));
	}

	private void appendEdge(StringBuilder builder, String from, String to) {
		builder.append(INDENT).append(quote(from)).append(" -> ").append(quote(to)).append(";")
				.append(System.lineSeparator());
	}

	private String quote(String label) {
		return "\"" + label.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
	}
}
