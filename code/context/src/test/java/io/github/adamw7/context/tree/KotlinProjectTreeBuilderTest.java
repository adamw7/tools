package io.github.adamw7.context.tree;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import io.github.adamw7.context.Language;

public class KotlinProjectTreeBuilderTest {

	@TempDir
	Path projectRoot;

	@Test
	void buildsFoldersFilesAndDependencies() throws IOException {
		Path pkg = createPackage("pkg");
		writeKotlin(pkg, "A", "class A");
		writeKotlin(pkg, "B", "class B { val a: A = A() }");

		ProjectTreeNode root = new ProjectTreeBuilder(Language.KOTLIN, 1).build(projectRoot);

		assertTrue(root.isDirectory());
		ProjectTreeNode pkgNode = onlyChild(root);
		assertEquals("pkg", pkgNode.name());
		assertEquals(2, pkgNode.children().size());
	}

	@Test
	void fileDependsOnUsedClass() throws IOException {
		Path pkg = createPackage("pkg");
		writeKotlin(pkg, "A", "class A");
		writeKotlin(pkg, "B", "class B { val a: A = A() }");

		ProjectTreeNode pkgNode = onlyChild(new ProjectTreeBuilder(Language.KOTLIN, 1).build(projectRoot));

		ProjectTreeNode b = child(pkgNode, "B.kt");
		assertEquals(1, b.dependencies().size());
		assertTrue(b.dependencies().contains("A.kt"));
	}

	@Test
	void selfReferenceIsNotADependency() throws IOException {
		Path pkg = createPackage("pkg");
		writeKotlin(pkg, "A", "class A");

		ProjectTreeNode pkgNode = onlyChild(new ProjectTreeBuilder(Language.KOTLIN, 1).build(projectRoot));

		ProjectTreeNode a = child(pkgNode, "A.kt");
		assertTrue(a.dependencies().isEmpty());
		assertFalse(a.isDirectory());
	}

	@Test
	void onlyKotlinFilesAreAnalysedForDependencies() throws IOException {
		Path pkg = createPackage("pkg");
		writeKotlin(pkg, "A", "class A");
		Files.writeString(pkg.resolve("Helper.java"), "public class Helper { A a; }");

		ProjectTreeNode pkgNode = onlyChild(new ProjectTreeBuilder(Language.KOTLIN, 1).build(projectRoot));

		assertEquals(2, pkgNode.children().size());
		ProjectTreeNode helper = child(pkgNode, "Helper.java");
		assertTrue(helper.dependencies().isEmpty());
	}

	private Path createPackage(String name) throws IOException {
		return Files.createDirectories(projectRoot.resolve(name));
	}

	private void writeKotlin(Path directory, String className, String body) throws IOException {
		Files.writeString(directory.resolve(className + ".kt"), body);
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
