package io.github.adamw7.context.tree;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

public class ProjectTreeJsonSerializerTest {

	private static final ObjectMapper MAPPER = new ObjectMapper();

	private final ProjectTreeJsonSerializer serializer = new ProjectTreeJsonSerializer();

	@Test
	void serializesNameAndTypeForADirectory() {
		ProjectTreeNode root = ProjectTreeNode.directory(Path.of("project"));

		JsonNode json = parse(serializer.serialize(root));

		assertEquals("project", json.get("name").asText());
		assertEquals("directory", json.get("type").asText());
	}

	@Test
	void serializesFilesAsTypeFile() {
		ProjectTreeNode root = ProjectTreeNode.directory(Path.of("project"));
		root.addChild(ProjectTreeNode.file(Path.of("project/A.java")));

		JsonNode json = parse(serializer.serialize(root));
		JsonNode child = json.get("children").get(0);

		assertEquals("file", child.get("type").asText());
		assertEquals("A.java", child.get("name").asText());
	}

	@Test
	void serializesDependenciesAsAnArray() {
		ProjectTreeNode root = ProjectTreeNode.directory(Path.of("project"));
		ProjectTreeNode file = ProjectTreeNode.file(Path.of("project/B.java"));
		file.addDependency("A.java");
		file.addDependency("C.java");
		root.addChild(file);

		JsonNode json = parse(serializer.serialize(root));
		JsonNode dependencies = json.get("children").get(0).get("dependencies");

		assertEquals(2, dependencies.size());
		assertTrue(textValues(dependencies).contains("A.java"));
		assertTrue(textValues(dependencies).contains("C.java"));
	}

	@Test
	void nestsChildrenRecursively() {
		ProjectTreeNode root = ProjectTreeNode.directory(Path.of("project"));
		ProjectTreeNode pkg = ProjectTreeNode.directory(Path.of("project/pkg"));
		pkg.addChild(ProjectTreeNode.file(Path.of("project/pkg/A.java")));
		root.addChild(pkg);

		JsonNode json = parse(serializer.serialize(root));
		JsonNode pkgJson = json.get("children").get(0);

		assertEquals("pkg", pkgJson.get("name").asText());
		assertEquals("A.java", pkgJson.get("children").get(0).get("name").asText());
	}

	@Test
	void prettyOutputIsParseableAndEquivalent() {
		ProjectTreeNode root = ProjectTreeNode.directory(Path.of("project"));
		root.addChild(ProjectTreeNode.file(Path.of("project/A.java")));

		JsonNode pretty = parse(serializer.serializePretty(root, 2));

		assertEquals("project", pretty.get("name").asText());
		assertEquals(1, pretty.get("children").size());
	}

	private JsonNode parse(String json) {
		try {
			return MAPPER.readTree(json);
		} catch (JsonProcessingException e) {
			throw new IllegalStateException("Invalid JSON: " + json, e);
		}
	}

	private List<String> textValues(JsonNode array) {
		List<String> values = new ArrayList<>();
		array.forEach(node -> values.add(node.asText()));
		return values;
	}
}
