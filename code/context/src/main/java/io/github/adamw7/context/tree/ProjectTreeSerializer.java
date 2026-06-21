package io.github.adamw7.context.tree;

/**
 * Renders a {@link ProjectTreeNode} tree into a textual representation. Concrete
 * serializers choose the format (indented text, Markdown, JSON, ...). Keeping
 * serialization behind this interface lets new formats be added without changing
 * the tree or its existing renderers, and lets callers (such as an MCP tool)
 * depend on the abstraction rather than a concrete format.
 */
public interface ProjectTreeSerializer {

	String serialize(ProjectTreeNode root);
}
