package io.github.adamw7.context.tree;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

public class JavaProjectTreeBuilderTest {

	@TempDir
	Path projectRoot;

	@Test
	void buildsFoldersFilesAndDependencies() throws IOException {
		Path pkg = createPackage("pkg");
		writeJava(pkg, "A", "public class A {}");
		writeJava(pkg, "B", "public class B { A a; }");

		ProjectTreeNode root = new ProjectTreeBuilder(1).build(projectRoot);

		assertTrue(root.isDirectory());
		ProjectTreeNode pkgNode = onlyChild(root);
		assertEquals("pkg", pkgNode.name());
		assertEquals(2, pkgNode.children().size());
	}

	@Test
	void fileDependsOnUsedClass() throws IOException {
		Path pkg = createPackage("pkg");
		writeJava(pkg, "A", "public class A {}");
		writeJava(pkg, "B", "public class B { A a; }");

		ProjectTreeNode pkgNode = onlyChild(new ProjectTreeBuilder(1).build(projectRoot));

		ProjectTreeNode b = child(pkgNode, "B.java");
		assertFalse(b.isDirectory());
		assertEquals(1, b.dependencies().size());
		assertTrue(b.dependencies().contains("A.java"));
	}

	@Test
	void transitiveDependenciesAppearAtDepthTwo() throws IOException {
		Path pkg = createPackage("pkg");
		writeJava(pkg, "A", "public class A {}");
		writeJava(pkg, "B", "public class B { A a; }");
		writeJava(pkg, "C", "public class C { B b; }");

		ProjectTreeNode pkgNode = onlyChild(new ProjectTreeBuilder(2).build(projectRoot));

		ProjectTreeNode c = child(pkgNode, "C.java");
		assertEquals(2, c.dependencies().size());
		assertTrue(c.dependencies().contains("A.java"));
		assertTrue(c.dependencies().contains("B.java"));
	}

	@Test
	void dependenciesAreListedInSortedOrder() throws IOException {
		Path pkg = createPackage("pkg");
		writeJava(pkg, "A", "public class A {}");
		writeJava(pkg, "C", "public class C {}");
		writeJava(pkg, "B", "public class B { C c; A a; }");

		ProjectTreeNode pkgNode = onlyChild(new ProjectTreeBuilder(1).build(projectRoot));

		ProjectTreeNode b = child(pkgNode, "B.java");
		assertEquals(java.util.List.of("A.java", "C.java"), java.util.List.copyOf(b.dependencies()));
	}

	@Test
	void selfReferenceIsNotADependency() throws IOException {
		Path pkg = createPackage("pkg");
		writeJava(pkg, "A", "public class A {}");

		ProjectTreeNode pkgNode = onlyChild(new ProjectTreeBuilder(1).build(projectRoot));

		ProjectTreeNode a = child(pkgNode, "A.java");
		assertTrue(a.dependencies().isEmpty());
		assertFalse(a.isDirectory());
	}

	@Test
	void directoriesAreListedBeforeFiles() throws IOException {
		writeJava(projectRoot, "Root", "public class Root {}");
		createPackage("sub");

		ProjectTreeNode root = new ProjectTreeBuilder(1).build(projectRoot);

		assertEquals(2, root.children().size());
		assertTrue(root.children().get(0).isDirectory());
		assertFalse(root.children().get(1).isDirectory());
	}

	private Path createPackage(String name) throws IOException {
		return Files.createDirectories(projectRoot.resolve(name));
	}

	private void writeJava(Path directory, String className, String body) throws IOException {
		Files.writeString(directory.resolve(className + ".java"), body);
	}

	private ProjectTreeNode onlyChild(ProjectTreeNode node) {
		assertEquals(1, node.children().size());
		return node.children().get(0);
	}

	private ProjectTreeNode child(ProjectTreeNode parent, String name) {
		return parent.children().stream()
				.filter(node -> node.name().equals(name))
				.findFirst()
				.orElseThrow();
	}
}
