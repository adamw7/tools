package io.github.adamw7.context.tree;

import java.util.LinkedHashMap;
import java.util.List;
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
public class ProjectTreeMermaidSerializer extends AbstractProjectTreeGraphSerializer {

	private static final String HEADER = "flowchart LR";
	private static final String ARROW = " --> ";
	private static final String NODE_PREFIX = "n";

	/** The ids are numbered per call, so one serializer renders any number of trees. */
	@Override
	protected String render(List<Edge> edges) {
		Map<String, String> ids = new LinkedHashMap<>();
		StringBuilder builder = new StringBuilder(HEADER).append(System.lineSeparator());
		edges.forEach(edge -> builder.append(INDENT).append(nodeFor(edge.from(), ids)).append(ARROW)
				.append(nodeFor(edge.to(), ids)).append(System.lineSeparator()));
		return builder.toString();
	}

	private String nodeFor(String label, Map<String, String> ids) {
		String id = ids.computeIfAbsent(label, key -> NODE_PREFIX + ids.size());
		return id + "[\"" + escape(label) + "\"]";
	}

	private String escape(String label) {
		return label.replace("\"", "#quot;");
	}
}
