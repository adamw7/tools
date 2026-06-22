package io.github.adamw7.context.tree;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;

import org.junit.jupiter.api.Test;

public class ProjectTreeDotSerializerTest {

	private final ProjectTreeDotSerializer serializer = new ProjectTreeDotSerializer();

	@Test
	void opensAndClosesADigraph() {
		String dot = serializer.serialize(ProjectTreeNode.directory(Path.of("project")));

		assertTrue(dot.startsWith("digraph project {"));
		assertTrue(dot.trim().endsWith("}"));
	}

	@Test
	void emitsAnEdgeFromAFileToEachDependency() {
		ProjectTreeNode root = ProjectTreeNode.directory(Path.of("project"));
		ProjectTreeNode file = ProjectTreeNode.file(Path.of("project/B.java"));
		file.addDependency("A.java");
		file.addDependency("C.java");
		root.addChild(file);

		String dot = serializer.serialize(root);

		assertTrue(dot.contains("\"B.java\" -> \"A.java\";"));
		assertTrue(dot.contains("\"B.java\" -> \"C.java\";"));
	}

	@Test
	void doesNotDrawDirectoriesAsNodes() {
		ProjectTreeNode root = ProjectTreeNode.directory(Path.of("project"));
		root.addChild(ProjectTreeNode.directory(Path.of("project/pkg")));

		String dot = serializer.serialize(root);

		assertFalse(dot.contains("->"));
	}

	@Test
	void recursesIntoNestedDirectories() {
		ProjectTreeNode root = ProjectTreeNode.directory(Path.of("project"));
		ProjectTreeNode pkg = ProjectTreeNode.directory(Path.of("project/pkg"));
		ProjectTreeNode file = ProjectTreeNode.file(Path.of("project/pkg/B.java"));
		file.addDependency("A.java");
		pkg.addChild(file);
		root.addChild(pkg);

		String dot = serializer.serialize(root);

		assertTrue(dot.contains("\"B.java\" -> \"A.java\";"));
	}

	@Test
	void escapesQuotesAndBackslashesInNames() {
		ProjectTreeNode root = ProjectTreeNode.directory(Path.of("project"));
		ProjectTreeNode file = ProjectTreeNode.file(Path.of("project/B.java"));
		file.addDependency("Wei\"rd");
		root.addChild(file);

		String dot = serializer.serialize(root);

		assertTrue(dot.contains("\\\""));
	}
}
