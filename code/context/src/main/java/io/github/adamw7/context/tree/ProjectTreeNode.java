package io.github.adamw7.context.tree;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * A node in the tree of a Java project. A node is either a {@link Type#DIRECTORY}
 * (a project folder holding child nodes) or a {@link Type#FILE} (a source or
 * resource file). File nodes additionally carry the set of classes they depend
 * on, so the tree describes folders, files and dependencies at once.
 */
public class ProjectTreeNode {

	public enum Type {
		DIRECTORY, FILE
	}

	private final String name;
	private final Type type;
	private final List<ProjectTreeNode> children = new ArrayList<>();
	private final Set<String> dependencies = new LinkedHashSet<>();

	public ProjectTreeNode(String name, Type type) {
		this.name = name;
		this.type = type;
	}

	public static ProjectTreeNode directory(Path path) {
		return new ProjectTreeNode(nameOf(path), Type.DIRECTORY);
	}

	public static ProjectTreeNode file(Path path) {
		return new ProjectTreeNode(nameOf(path), Type.FILE);
	}

	private static String nameOf(Path path) {
		Path fileName = path.getFileName();
		return fileName == null ? path.toString() : fileName.toString();
	}

	public void addChild(ProjectTreeNode child) {
		children.add(child);
	}

	public void addDependency(String dependency) {
		dependencies.add(dependency);
	}

	public boolean isDirectory() {
		return type == Type.DIRECTORY;
	}

	public String name() {
		return name;
	}

	public List<ProjectTreeNode> children() {
		return children;
	}

	public Set<String> dependencies() {
		return dependencies;
	}
}
