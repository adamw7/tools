package io.github.adamw7.context.tree;

/**
 * Renders a {@link ProjectTreeNode} tree as indented text. Directories and
 * files are listed hierarchically and each file's dependencies are printed
 * beneath it, giving a compact, human- and LLM-readable view of the project.
 */
public class ProjectTreePrinter extends AbstractProjectTreeIndentedSerializer {

	private static final String DIRECTORY_MARKER = "[dir] ";
	private static final String FILE_MARKER = "[file] ";
	private static final String DEPENDENCY_MARKER = "-> ";

	/** The printer's own name for {@link #serialize}, kept for callers that hold one directly. */
	public String print(ProjectTreeNode root) {
		return serialize(root);
	}

	@Override
	protected String line(ProjectTreeNode node) {
		return (node.isDirectory() ? DIRECTORY_MARKER : FILE_MARKER) + node.name();
	}

	@Override
	protected String dependencyLine(String dependency) {
		return DEPENDENCY_MARKER + dependency;
	}
}
