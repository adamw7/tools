package io.github.adamw7.context.tree;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.jupiter.api.Test;

public class ProjectTreeJsonSerializerTest {

	private final ProjectTreeJsonSerializer serializer = new ProjectTreeJsonSerializer();

	@Test
	void serializesNameAndTypeForADirectory() {
		ProjectTreeNode root = ProjectTreeNode.directory(Path.of("project"));

		JSONObject json = new JSONObject(serializer.serialize(root));

		assertEquals("project", json.getString("name"));
		assertEquals("directory", json.getString("type"));
	}

	@Test
	void serializesFilesAsTypeFile() {
		ProjectTreeNode root = ProjectTreeNode.directory(Path.of("project"));
		root.addChild(ProjectTreeNode.file(Path.of("project/A.java")));

		JSONObject json = new JSONObject(serializer.serialize(root));
		JSONObject child = json.getJSONArray("children").getJSONObject(0);

		assertEquals("file", child.getString("type"));
		assertEquals("A.java", child.getString("name"));
	}

	@Test
	void serializesDependenciesAsAnArray() {
		ProjectTreeNode root = ProjectTreeNode.directory(Path.of("project"));
		ProjectTreeNode file = ProjectTreeNode.file(Path.of("project/B.java"));
		file.addDependency("A.java");
		file.addDependency("C.java");
		root.addChild(file);

		JSONObject json = new JSONObject(serializer.serialize(root));
		JSONArray dependencies = json.getJSONArray("children").getJSONObject(0).getJSONArray("dependencies");

		assertEquals(2, dependencies.length());
		assertTrue(dependencies.toList().contains("A.java"));
		assertTrue(dependencies.toList().contains("C.java"));
	}

	@Test
	void nestsChildrenRecursively() {
		ProjectTreeNode root = ProjectTreeNode.directory(Path.of("project"));
		ProjectTreeNode pkg = ProjectTreeNode.directory(Path.of("project/pkg"));
		pkg.addChild(ProjectTreeNode.file(Path.of("project/pkg/A.java")));
		root.addChild(pkg);

		JSONObject json = new JSONObject(serializer.serialize(root));
		JSONObject pkgJson = json.getJSONArray("children").getJSONObject(0);

		assertEquals("pkg", pkgJson.getString("name"));
		assertEquals("A.java", pkgJson.getJSONArray("children").getJSONObject(0).getString("name"));
	}

	@Test
	void prettyOutputIsParseableAndEquivalent() {
		ProjectTreeNode root = ProjectTreeNode.directory(Path.of("project"));
		root.addChild(ProjectTreeNode.file(Path.of("project/A.java")));

		JSONObject pretty = new JSONObject(serializer.serializePretty(root, 2));

		assertEquals("project", pretty.getString("name"));
		assertEquals(1, pretty.getJSONArray("children").length());
	}
}
