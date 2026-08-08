package io.github.adamw7.context.mcp;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
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

	private Map<String, Object> arguments() {
		return pathArgument(projectRoot);
	}

	private String rendered(String format) {
		Map<String, Object> arguments = arguments();
		arguments.put("format", format);
		return tool.apply(arguments).text();
	}
}
