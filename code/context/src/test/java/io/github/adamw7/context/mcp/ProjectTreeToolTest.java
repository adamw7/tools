package io.github.adamw7.context.mcp;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import io.github.adamw7.tools.mcp.ToolResult;

public class ProjectTreeToolTest {

	@TempDir
	Path projectRoot;

	@TempDir
	Path outsideRoot;

	private static final ObjectMapper MAPPER = new ObjectMapper();

	private ProjectTreeTool tool;

	@BeforeEach
	void setUp() {
		tool = new ProjectTreeTool(new PathPolicy(projectRoot.toString()));
	}

	@Test
	void toolDefinitionExposesName() {
		assertEquals("project_tree", tool.getToolDefinition().name());
	}

	@Test
	@SuppressWarnings("unchecked")
	void toolDefinitionRequiresPath() {
		List<String> required = (List<String>) tool.getToolDefinition().inputSchema().get("required");
		assertTrue(required.contains("path"));
	}

	@Test
	void buildsJsonTreeByDefault() throws IOException {
		writeJava("A", "public class A {}");
		writeJava("B", "public class B { A a; }");

		JsonNode tree = MAPPER.readTree(text(tool.apply(arguments())));

		assertEquals(projectRoot.getFileName().toString(), tree.get("name").asText());
		assertEquals("directory", tree.get("type").asText());
		assertEquals(2, tree.get("children").size());
	}

	@Test
	void honoursTheMarkdownFormat() throws IOException {
		writeJava("A", "public class A {}");

		Map<String, Object> arguments = arguments();
		arguments.put("format", "markdown");

		String rendered = text(tool.apply(arguments));
		assertTrue(rendered.contains("- `A.java`"));
	}

	@Test
	void honoursTheTextFormat() throws IOException {
		writeJava("A", "public class A {}");

		Map<String, Object> arguments = arguments();
		arguments.put("format", "text");

		String rendered = text(tool.apply(arguments));
		assertTrue(rendered.contains("[file] A.java"));
	}

	@Test
	void honoursTheMermaidFormat() throws IOException {
		writeJava("A", "public class A {}");
		writeJava("B", "public class B { A a; }");

		Map<String, Object> arguments = arguments();
		arguments.put("format", "mermaid");

		String rendered = text(tool.apply(arguments));
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

		String rendered = text(tool.apply(arguments));
		assertTrue(rendered.contains("A.java"));
		assertTrue(rendered.contains("B.java"));
	}

	@Test
	void resultIsNotAnError() throws IOException {
		writeJava("A", "public class A {}");
		assertFalse(tool.apply(arguments()).isError());
	}

	@Test
	void missingPathIsRejected() {
		assertThrows(IllegalArgumentException.class, () -> tool.apply(new HashMap<>()));
	}

	@Test
	void pathOutsideTheAllowedRootIsRejected() {
		Map<String, Object> arguments = new HashMap<>();
		arguments.put("path", outsideRoot.toString());
		assertThrows(SecurityException.class, () -> tool.apply(arguments));
	}

	@Test
	void excessiveDepthIsRejected() throws IOException {
		writeJava("A", "public class A {}");
		Map<String, Object> arguments = arguments();
		arguments.put("depth", 99);
		assertThrows(IllegalArgumentException.class, () -> tool.apply(arguments));
	}

	private Map<String, Object> arguments() {
		Map<String, Object> arguments = new HashMap<>();
		arguments.put("path", projectRoot.toString());
		return arguments;
	}

	private void writeJava(String className, String body) throws IOException {
		Files.writeString(projectRoot.resolve(className + ".java"), body);
	}

	private String text(ToolResult result) {
		return result.text();
	}
}
