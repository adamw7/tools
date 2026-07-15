package io.github.adamw7.context.tree;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Renders the dependency graph of a {@link ProjectTreeNode} tree as a
 * <a href="https://mermaid.js.org/syntax/flowchart.html">Mermaid</a> flowchart:
 * every file becomes a node and every dependency it carries becomes a directed
 * edge from the file to the depended-on class. Directories contribute structure
 * but are not drawn, so the result is a focused view of how the project's classes
 * depend on one another — like the Graphviz DOT serializer, but in a format that
 * renders inline on GitHub, in Markdown viewers and in many gen-AI agent surfaces
 * without any external tooling.
 */
public class ProjectTreeMermaidSerializer implements ProjectTreeSerializer {

	private static final String INDENT = "  ";
	private static final String HEADER = "flowchart LR";
	private static final String ARROW = " --> ";
	private static final String NODE_PREFIX = "n";

	@Override
	public String serialize(ProjectTreeNode root) {
		StringBuilder builder = new StringBuilder();
		builder.append(HEADER).append(System.lineSeparator());
		appendEdges(builder, root, new LinkedHashMap<>());
		return builder.toString();
	}

	private void appendEdges(StringBuilder builder, ProjectTreeNode node, Map<String, String> ids) {
		appendNodeEdges(builder, node, ids);
		node.children().forEach(child -> appendEdges(builder, child, ids));
	}

	private void appendNodeEdges(StringBuilder builder, ProjectTreeNode node, Map<String, String> ids) {
		if (node.isDirectory()) {
			return;
		}
		node.dependencies().forEach(dependency -> appendEdge(builder, node.name(), dependency, ids));
	}

	private void appendEdge(StringBuilder builder, String from, String to, Map<String, String> ids) {
		builder.append(INDENT).append(nodeFor(from, ids)).append(ARROW).append(nodeFor(to, ids))
				.append(System.lineSeparator());
	}

	private String nodeFor(String label, Map<String, String> ids) {
		String id = ids.computeIfAbsent(label, key -> NODE_PREFIX + ids.size());
		return id + "[\"" + escape(label) + "\"]";
	}

	private String escape(String label) {
		return label.replace("\"", "#quot;");
	}
}
