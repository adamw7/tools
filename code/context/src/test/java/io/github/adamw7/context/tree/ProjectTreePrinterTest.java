package io.github.adamw7.context.tree;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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
		assertTrue(output.indexOf("[file] B.java") < output.indexOf("-> A.java"),
				"a file must be printed before its dependencies");
	}

	@Test
	void printsEachDependencyOnItsOwnIndentedLine() {
		ProjectTreeNode root = ProjectTreeNode.directory(Path.of("project"));
		ProjectTreeNode file = ProjectTreeNode.file(Path.of("project/B.java"));
		file.addDependency("A.java");
		file.addDependency("C.java");
		root.addChild(file);

		String output = printer.print(root);

		assertTrue(output.contains("    -> A.java"));
		assertTrue(output.contains("    -> C.java"));
		assertEquals(4, output.split(System.lineSeparator()).length);
	}

	@Test
	void fileWithoutDependenciesHasNoArrow() {
		ProjectTreeNode root = ProjectTreeNode.directory(Path.of("project"));
		root.addChild(ProjectTreeNode.file(Path.of("project/A.java")));

		String output = printer.print(root);

		assertTrue(output.contains("[file] A.java"));
		assertFalse(output.contains("->"));
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
