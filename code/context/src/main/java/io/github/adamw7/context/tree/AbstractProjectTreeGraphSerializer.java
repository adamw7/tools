package io.github.adamw7.context.tree;

import java.util.ArrayList;
import java.util.List;

/**
 * Shared skeleton for the serializers that render the tree's dependency graph
 * rather than its shape: every file becomes a node and every dependency a directed
 * edge to the depended-on class, with directories contributing structure but not
 * drawn. Which edges there are is the same question whatever the target syntax, so
 * the walk lives here and a subclass only says how to write them out — the
 * difference between {@link ProjectTreeDotSerializer} and
 * {@link ProjectTreeMermaidSerializer}. Collecting them first also lets a format
 * that numbers its nodes hold that state for one call, so a serializer stays
 * reusable.
 */
public abstract class AbstractProjectTreeGraphSerializer implements ProjectTreeSerializer {

	protected static final String INDENT = "  ";

	/** One directed edge, from the file that declares the dependency to the class it depends on. */
	protected record Edge(String from, String to) {
	}

	@Override
	public final String serialize(ProjectTreeNode root) {
		List<Edge> edges = new ArrayList<>();
		collect(root, edges);
		return render(edges);
	}

	/** Writes the whole graph, header and all, in the target syntax. */
	protected abstract String render(List<Edge> edges);

	private void collect(ProjectTreeNode node, List<Edge> edges) {
		if (!node.isDirectory()) {
			node.dependencies().forEach(dependency -> edges.add(new Edge(node.name(), dependency)));
		}
		node.children().forEach(child -> collect(child, edges));
	}
}
