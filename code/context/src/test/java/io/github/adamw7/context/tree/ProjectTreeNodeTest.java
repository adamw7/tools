package io.github.adamw7.context.tree;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;

import io.github.adamw7.context.tree.ProjectTreeNode.Type;

public class ProjectTreeNodeTest {

	@Test
	public void directoryFactoryUsesLastPathSegmentAsName() {
		ProjectTreeNode node = ProjectTreeNode.directory(Path.of("src", "main", "java"));

		assertEquals("java", node.name());
		assertTrue(node.isDirectory());
	}

	@Test
	public void fileFactoryUsesFileNameAsName() {
		ProjectTreeNode node = ProjectTreeNode.file(Path.of("src", "Main.java"));

		assertEquals("Main.java", node.name());
		assertFalse(node.isDirectory());
	}

	@Test
	public void rootPathWithoutFileNameFallsBackToFullPath() {
		Path root = Path.of("/");

		ProjectTreeNode node = ProjectTreeNode.directory(root);

		assertEquals(root.toString(), node.name());
	}

	@Test
	public void newNodeHasNoChildren() {
		ProjectTreeNode node = new ProjectTreeNode("root", Type.DIRECTORY);

		assertTrue(node.children().isEmpty());
	}

	@Test
	public void addChildAppendsInInsertionOrder() {
		ProjectTreeNode root = new ProjectTreeNode("root", Type.DIRECTORY);
		ProjectTreeNode first = new ProjectTreeNode("a", Type.FILE);
		ProjectTreeNode second = new ProjectTreeNode("b", Type.FILE);

		root.addChild(first);
		root.addChild(second);

		assertEquals(List.of(first, second), root.children());
	}

	@Test
	public void newNodeHasNoDependencies() {
		ProjectTreeNode node = new ProjectTreeNode("Main.java", Type.FILE);

		assertTrue(node.dependencies().isEmpty());
	}

	@Test
	public void addDependencyStoresTheDependency() {
		ProjectTreeNode node = new ProjectTreeNode("Main.java", Type.FILE);

		node.addDependency("com.example.Foo");

		assertTrue(node.dependencies().contains("com.example.Foo"));
	}

	@Test
	public void duplicateDependenciesAreDeduplicated() {
		ProjectTreeNode node = new ProjectTreeNode("Main.java", Type.FILE);

		node.addDependency("com.example.Foo");
		node.addDependency("com.example.Foo");

		assertEquals(1, node.dependencies().size());
	}

	@Test
	public void dependenciesPreserveInsertionOrder() {
		ProjectTreeNode node = new ProjectTreeNode("Main.java", Type.FILE);

		node.addDependency("com.example.C");
		node.addDependency("com.example.A");
		node.addDependency("com.example.B");

		assertEquals(List.of("com.example.C", "com.example.A", "com.example.B"),
				List.copyOf(node.dependencies()));
	}

	@Test
	public void fileNodeIsNotADirectory() {
		ProjectTreeNode node = new ProjectTreeNode("Main.java", Type.FILE);

		assertFalse(node.isDirectory());
	}
}
