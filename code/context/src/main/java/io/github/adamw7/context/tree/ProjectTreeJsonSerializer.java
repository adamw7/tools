package io.github.adamw7.context.tree;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.util.DefaultIndenter;
import com.fasterxml.jackson.core.util.DefaultPrettyPrinter;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

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
	private static final ObjectMapper MAPPER = new ObjectMapper();

	@Override
	public String serialize(ProjectTreeNode root) {
		return toJson(root).toString();
	}

	public String serializePretty(ProjectTreeNode root, int indentFactor) {
		try {
			return MAPPER.writer(prettyPrinter(indentFactor)).writeValueAsString(toJson(root));
		} catch (JsonProcessingException e) {
			throw new IllegalStateException("Failed to serialize project tree", e);
		}
	}

	private DefaultPrettyPrinter prettyPrinter(int indentFactor) {
		DefaultIndenter indenter = new DefaultIndenter(" ".repeat(indentFactor), "\n");
		DefaultPrettyPrinter printer = new DefaultPrettyPrinter();
		printer.indentObjectsWith(indenter);
		printer.indentArraysWith(indenter);
		return printer;
	}

	private ObjectNode toJson(ProjectTreeNode node) {
		ObjectNode object = MAPPER.createObjectNode();
		object.put("name", node.name());
		object.put("type", node.isDirectory() ? DIRECTORY_TYPE : FILE_TYPE);
		object.set("dependencies", dependenciesOf(node));
		object.set("children", childrenOf(node));
		return object;
	}

	private ArrayNode dependenciesOf(ProjectTreeNode node) {
		ArrayNode dependencies = MAPPER.createArrayNode();
		node.dependencies().forEach(dependencies::add);
		return dependencies;
	}

	private ArrayNode childrenOf(ProjectTreeNode node) {
		ArrayNode children = MAPPER.createArrayNode();
		node.children().forEach(child -> children.add(toJson(child)));
		return children;
	}
}
