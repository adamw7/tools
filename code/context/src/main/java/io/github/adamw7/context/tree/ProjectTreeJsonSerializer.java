package io.github.adamw7.context.tree;

import org.json.JSONArray;
import org.json.JSONObject;

/**
 * Renders a {@link ProjectTreeNode} tree as JSON. Each node becomes an object
 * with its {@code name}, {@code type} ({@code directory} or {@code file}), the
 * {@code dependencies} it carries, and its {@code children}. The structured
 * output is convenient for programmatic consumers such as an MCP tool that hands
 * the context to a gen-AI agent.
 */
public class ProjectTreeJsonSerializer implements ProjectTreeSerializer {

	private static final String DIRECTORY_TYPE = "directory";
	private static final String FILE_TYPE = "file";

	@Override
	public String serialize(ProjectTreeNode root) {
		return toJson(root).toString();
	}

	public String serializePretty(ProjectTreeNode root, int indentFactor) {
		return toJson(root).toString(indentFactor);
	}

	private JSONObject toJson(ProjectTreeNode node) {
		JSONObject object = new JSONObject();
		object.put("name", node.name());
		object.put("type", node.isDirectory() ? DIRECTORY_TYPE : FILE_TYPE);
		object.put("dependencies", new JSONArray(node.dependencies()));
		object.put("children", childrenOf(node));
		return object;
	}

	private JSONArray childrenOf(ProjectTreeNode node) {
		JSONArray children = new JSONArray();
		node.children().forEach(child -> children.put(toJson(child)));
		return children;
	}
}
