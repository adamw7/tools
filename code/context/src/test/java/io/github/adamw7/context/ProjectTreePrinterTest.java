package io.github.adamw7.context;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;

import org.junit.jupiter.api.Test;

public class ProjectTreePrinterTest {

	private final ProjectTreePrinter printer = new ProjectTreePrinter();

	@Test
	void rendersDirectoriesFilesAndDependencies() {
		ProjectTreeNode root = ProjectTreeNode.directory(Path.of("project"));
		ProjectTreeNode file = ProjectTreeNode.file(Path.of("project/B.java"));
		file.addDependency("A.java");
		root.addChild(file);

		String output = printer.print(root);

		assertTrue(output.contains("[dir] project"));
		assertTrue(output.contains("[file] B.java"));
		assertTrue(output.contains("-> A.java"));
	}

	@Test
	void nestsChildrenWithIndentation() {
		ProjectTreeNode root = ProjectTreeNode.directory(Path.of("project"));
		ProjectTreeNode pkg = ProjectTreeNode.directory(Path.of("project/pkg"));
		root.addChild(pkg);

		String output = printer.print(root);

		assertTrue(output.contains("  [dir] pkg"));
	}
}
