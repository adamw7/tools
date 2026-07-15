package io.github.adamw7.context.tree;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;

import org.junit.jupiter.api.Test;

public class ProjectTreeMermaidSerializerTest {

	private final ProjectTreeMermaidSerializer serializer = new ProjectTreeMermaidSerializer();

	@Test
	void opensWithAFlowchartHeader() {
		String mermaid = serializer.serialize(ProjectTreeNode.directory(Path.of("project")));

		assertTrue(mermaid.startsWith("flowchart LR"));
	}

	@Test
	void emitsAnEdgeFromAFileToEachDependency() {
		ProjectTreeNode root = ProjectTreeNode.directory(Path.of("project"));
		ProjectTreeNode file = ProjectTreeNode.file(Path.of("project/B.java"));
		file.addDependency("A.java");
		file.addDependency("C.java");
		root.addChild(file);

		String mermaid = serializer.serialize(root);

		assertTrue(mermaid.contains("[\"B.java\"] --> "));
		assertTrue(mermaid.contains("[\"A.java\"]"));
		assertTrue(mermaid.contains("[\"C.java\"]"));
	}

	@Test
	void reusesTheSameIdForARepeatedLabel() {
		ProjectTreeNode root = ProjectTreeNode.directory(Path.of("project"));
		ProjectTreeNode first = ProjectTreeNode.file(Path.of("project/B.java"));
		first.addDependency("A.java");
		ProjectTreeNode second = ProjectTreeNode.file(Path.of("project/C.java"));
		second.addDependency("A.java");
		root.addChild(first);
		root.addChild(second);

		String mermaid = serializer.serialize(root);

		assertTrue(mermaid.contains("n0[\"B.java\"] --> n1[\"A.java\"]"));
		assertTrue(mermaid.contains("n2[\"C.java\"] --> n1[\"A.java\"]"));
	}

	@Test
	void doesNotDrawDirectoriesAsNodes() {
		ProjectTreeNode root = ProjectTreeNode.directory(Path.of("project"));
		root.addChild(ProjectTreeNode.directory(Path.of("project/pkg")));

		String mermaid = serializer.serialize(root);

		assertFalse(mermaid.contains("-->"));
	}

	@Test
	void recursesIntoNestedDirectories() {
		ProjectTreeNode root = ProjectTreeNode.directory(Path.of("project"));
		ProjectTreeNode pkg = ProjectTreeNode.directory(Path.of("project/pkg"));
		ProjectTreeNode file = ProjectTreeNode.file(Path.of("project/pkg/B.java"));
		file.addDependency("A.java");
		pkg.addChild(file);
		root.addChild(pkg);

		String mermaid = serializer.serialize(root);

		assertTrue(mermaid.contains("[\"B.java\"] --> "));
		assertTrue(mermaid.contains("[\"A.java\"]"));
	}

	@Test
	void escapesDoubleQuotesInNames() {
		ProjectTreeNode root = ProjectTreeNode.directory(Path.of("project"));
		ProjectTreeNode file = ProjectTreeNode.file(Path.of("project/B.java"));
		file.addDependency("Wei\"rd");
		root.addChild(file);

		String mermaid = serializer.serialize(root);

		assertTrue(mermaid.contains("Wei#quot;rd"));
		assertFalse(mermaid.contains("Wei\"rd"));
	}

	@Test
	void emitsNoEdgesForAFileWithoutDependencies() {
		ProjectTreeNode root = ProjectTreeNode.directory(Path.of("project"));
		root.addChild(ProjectTreeNode.file(Path.of("project/A.java")));

		String mermaid = serializer.serialize(root);

		assertFalse(mermaid.contains("-->"));
	}

	@Test
	void preservesDependencyInsertionOrder() {
		ProjectTreeNode root = ProjectTreeNode.directory(Path.of("project"));
		ProjectTreeNode file = ProjectTreeNode.file(Path.of("project/B.java"));
		file.addDependency("A.java");
		file.addDependency("C.java");
		root.addChild(file);

		String mermaid = serializer.serialize(root);

		assertTrue(mermaid.indexOf("\"A.java\"") < mermaid.indexOf("\"C.java\""));
	}

	@Test
	void emitsEdgesForEveryFileInTheTree() {
		ProjectTreeNode root = ProjectTreeNode.directory(Path.of("project"));
		ProjectTreeNode first = ProjectTreeNode.file(Path.of("project/B.java"));
		first.addDependency("A.java");
		ProjectTreeNode second = ProjectTreeNode.file(Path.of("project/D.java"));
		second.addDependency("C.java");
		root.addChild(first);
		root.addChild(second);

		String mermaid = serializer.serialize(root);

		assertTrue(mermaid.contains("[\"B.java\"] --> "));
		assertTrue(mermaid.contains("[\"D.java\"] --> "));
	}
}
