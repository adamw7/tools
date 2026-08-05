package io.github.adamw7.context.tree;

import java.util.List;

/**
 * Renders the dependency graph of a {@link ProjectTreeNode} tree as a Graphviz
 * <a href="https://graphviz.org/doc/info/lang.html">DOT</a> digraph: every file
 * becomes a node and every dependency it carries becomes a directed edge from the
 * file to the depended-on class. Directories contribute structure but are not
 * drawn, so the result is a focused, render-ready view of how the project's
 * classes depend on one another — complementary to the tree-shaped text, Markdown
 * and JSON serializers.
 */
public class ProjectTreeDotSerializer extends AbstractProjectTreeGraphSerializer {

	@Override
	protected String render(List<Edge> edges) {
		StringBuilder builder = new StringBuilder("digraph project {").append(System.lineSeparator());
		edges.forEach(edge -> builder.append(INDENT).append(quote(edge.from())).append(" -> ")
				.append(quote(edge.to())).append(";").append(System.lineSeparator()));
		return builder.append("}").append(System.lineSeparator()).toString();
	}

	private String quote(String label) {
		return "\"" + label.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
	}
}
