package io.github.adamw7.context.tree;

/**
 * Shared skeleton for the serializers that render the tree's shape rather than its
 * dependency graph: every node on a line of its own, indented one level per folder,
 * with the classes a file depends on listed beneath it. How deep a node sits is the
 * same question whatever the target syntax, so the walk lives here and a subclass
 * only says how a node and a dependency are written — the difference between
 * {@link ProjectTreePrinter} and {@link ProjectTreeMarkdownSerializer}.
 */
public abstract class AbstractProjectTreeIndentedSerializer implements ProjectTreeSerializer {

	protected static final String INDENT = "  ";

	@Override
	public final String serialize(ProjectTreeNode root) {
		StringBuilder builder = new StringBuilder();
		append(builder, root, "");
		return builder.toString();
	}

	/** How the node itself is written, without its indentation or line terminator. */
	protected abstract String line(ProjectTreeNode node);

	/** How one class the node depends on is written, without its indentation or line terminator. */
	protected abstract String dependencyLine(String dependency);

	private void append(StringBuilder builder, ProjectTreeNode node, String indent) {
		builder.append(indent).append(line(node)).append(System.lineSeparator());
		String deeper = indent + INDENT;
		node.dependencies().forEach(dependency ->
				builder.append(deeper).append(dependencyLine(dependency)).append(System.lineSeparator()));
		node.children().forEach(child -> append(builder, child, deeper));
	}
}
