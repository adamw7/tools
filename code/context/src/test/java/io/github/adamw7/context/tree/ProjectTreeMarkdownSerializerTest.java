package io.github.adamw7.context.tree;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;

import org.junit.jupiter.api.Test;

public class ProjectTreeMarkdownSerializerTest {

	private final ProjectTreeMarkdownSerializer serializer = new ProjectTreeMarkdownSerializer();

	@Test
	void rendersDirectoriesAsBoldBullets() {
		ProjectTreeNode root = ProjectTreeNode.directory(Path.of("project"));

		String output = serializer.serialize(root);

		assertTrue(output.contains("- **project**"));
	}

	@Test
	void rendersFilesAsCodeSpannedBullets() {
		ProjectTreeNode root = ProjectTreeNode.directory(Path.of("project"));
		root.addChild(ProjectTreeNode.file(Path.of("project/A.java")));

		String output = serializer.serialize(root);

		assertTrue(output.contains("- `A.java`"));
	}

	@Test
	void rendersDependenciesAsIndentedChildBullets() {
		ProjectTreeNode root = ProjectTreeNode.directory(Path.of("project"));
		ProjectTreeNode file = ProjectTreeNode.file(Path.of("project/B.java"));
		file.addDependency("A.java");
		root.addChild(file);

		String output = serializer.serialize(root);

		assertTrue(output.contains("- depends on: `A.java`"));
		assertTrue(output.indexOf("`B.java`") < output.indexOf("depends on: `A.java`"),
				"a file must be rendered before its dependencies");
	}

	@Test
	void nestsChildrenWithDeeperIndentation() {
		ProjectTreeNode root = ProjectTreeNode.directory(Path.of("project"));
		root.addChild(ProjectTreeNode.directory(Path.of("project/pkg")));

		String output = serializer.serialize(root);

		assertTrue(output.contains("  - **pkg**"));
	}
}
