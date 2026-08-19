package io.github.adamw7.context.mcp;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class ProjectTreeToolTest extends AbstractContextToolTest {

	private ProjectTreeTool tool;

	@BeforeEach
	void setUp() {
		tool = new ProjectTreeTool(new PathPolicy(projectRoot.toString()));
	}

	@Override
	protected ContextTool tool() {
		return tool;
	}

	@Test
	void toolDefinitionExposesName() {
		assertEquals("project_tree", tool.getToolDefinition().name());
	}

	@Test
	void buildsJsonTreeByDefault() throws IOException {
		writeJava("A", "public class A {}");
		writeJava("B", "public class B { A a; }");

		JsonNode tree = MAPPER.readTree(tool.apply(arguments()).text());

		assertEquals(projectRoot.getFileName().toString(), tree.get("name").asText());
		assertEquals("directory", tree.get("type").asText());
		assertEquals(2, tree.get("children").size());
	}

	@Test
	void honoursTheMarkdownFormat() throws IOException {
		writeJava("A", "public class A {}");

		assertTrue(rendered("markdown").contains("- `A.java`"));
	}

	@Test
	void honoursTheTextFormat() throws IOException {
		writeJava("A", "public class A {}");

		assertTrue(rendered("text").contains("[file] A.java"));
	}

	@Test
	void honoursTheMermaidFormat() throws IOException {
		writeJava("A", "public class A {}");
		writeJava("B", "public class B { A a; }");

		String rendered = rendered("mermaid");

		assertTrue(rendered.startsWith("flowchart LR"));
		assertTrue(rendered.contains("[\"B.java\"] --> "));
		assertTrue(rendered.contains("[\"A.java\"]"));
	}

	@Test
	void resolvesTransitiveDependenciesAtTheRequestedDepth() throws IOException {
		writeJava("A", "public class A {}");
		writeJava("B", "public class B { A a; }");
		writeJava("C", "public class C { B b; }");

		Map<String, Object> arguments = arguments();
		arguments.put("depth", 2);

		String rendered = tool.apply(arguments).text();

		assertTrue(rendered.contains("A.java"));
		assertTrue(rendered.contains("B.java"));
	}

	@Test
	void resultIsNotAnError() throws IOException {
		writeJava("A", "public class A {}");

		assertFalse(tool.apply(arguments()).isError());
	}

	@Test
	void twoClassesSharingASimpleNameAreToldApartByTheirPackage() throws IOException {
		// The name-based finder resolved either Dup to whichever was indexed first, so
		// the one that lost drew an edge to the other — a self-edge, by the only name
		// the tree carries. The package-aware finder resolves each within its own
		// package, where neither depends on anything.
		Path one = Files.createDirectory(projectRoot.resolve("one"));
		Path two = Files.createDirectory(projectRoot.resolve("two"));
		Files.writeString(one.resolve("Dup.java"), "package one;\npublic class Dup {}");
		Files.writeString(two.resolve("Dup.java"), "package two;\npublic class Dup {}");
		Files.writeString(one.resolve("User.java"), "package one;\npublic class User { Dup dup; }");

		JsonNode tree = MAPPER.readTree(tool.apply(arguments()).text());

		List<JsonNode> duplicates = nodesNamed(tree, "Dup.java");
		assertEquals(2, duplicates.size());
		duplicates.forEach(node -> assertTrue(node.get("dependencies").isEmpty(),
				"a class must not depend on a class of its own name: " + node));
	}

	private List<JsonNode> nodesNamed(JsonNode node, String name) {
		List<JsonNode> found = new ArrayList<>();
		if (name.equals(node.get("name").asText())) {
			found.add(node);
		}
		node.get("children").forEach(child -> found.addAll(nodesNamed(child, name)));
		return found;
	}

	private Map<String, Object> arguments() {
		return pathArgument(projectRoot);
	}

	private String rendered(String format) {
		Map<String, Object> arguments = arguments();
		arguments.put("format", format);
		return tool.apply(arguments).text();
	}
}
