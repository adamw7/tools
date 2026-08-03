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

public class OkfBundleToolTest {

	@TempDir
	Path projectRoot;

	@TempDir
	Path outsideRoot;

	private static final ObjectMapper MAPPER = new ObjectMapper();

	private OkfBundleTool tool;

	@BeforeEach
	void setUp() {
		tool = new OkfBundleTool(new PathPolicy(projectRoot.toString()));
	}

	@Test
	void toolDefinitionExposesName() {
		assertEquals("okf_bundle", tool.getToolDefinition().name());
	}

	@Test
	@SuppressWarnings("unchecked")
	void toolDefinitionRequiresPath() {
		List<String> required = (List<String>) tool.getToolDefinition().inputSchema().get("required");
		assertTrue(required.contains("path"));
	}

	@Test
	void returnsTheTargetedSpecificationVersion() throws IOException {
		writeJava("A", "public class A {}");

		JsonNode bundle = MAPPER.readTree(text(tool.apply(arguments())));

		assertEquals("0.2", bundle.get("okf_version").asText());
	}

	@Test
	void returnsOneMarkdownDocumentPerFileBesideTheRootIndex() throws IOException {
		writeJava("A", "public class A {}");
		writeJava("B", "public class B { A a; }");

		JsonNode documents = MAPPER.readTree(text(tool.apply(arguments()))).get("documents");

		assertEquals(3, documents.size());
		assertTrue(documents.has("index.md"));
		assertTrue(documents.has("A.java.md"));
		assertTrue(documents.has("B.java.md"));
	}

	@Test
	void conceptDocumentsCarryFrontmatterWithAType() throws IOException {
		writeJava("A", "public class A {}");

		JsonNode documents = MAPPER.readTree(text(tool.apply(arguments()))).get("documents");

		assertTrue(documents.get("A.java.md").asText().startsWith("---"));
		assertTrue(documents.get("A.java.md").asText().contains("type: \"Java Source File\""));
	}

	@Test
	void linksADependencyToTheConceptThatDescribesIt() throws IOException {
		writeJava("A", "public class A {}");
		writeJava("B", "public class B { A a; }");

		JsonNode documents = MAPPER.readTree(text(tool.apply(arguments()))).get("documents");

		assertTrue(documents.get("B.java.md").asText().contains("[`A.java`](/A.java.md)"));
	}

	@Test
	void honoursTheRequestedLanguage() throws IOException {
		Files.writeString(projectRoot.resolve("A.kt"), "class A");

		Map<String, Object> arguments = arguments();
		arguments.put("language", "kotlin");

		JsonNode documents = MAPPER.readTree(text(tool.apply(arguments))).get("documents");
		assertTrue(documents.get("A.kt.md").asText().contains("type: \"Kotlin Source File\""));
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

	@Test
	void scanningWritesNothingToTheProject() throws IOException {
		writeJava("A", "public class A {}");

		tool.apply(arguments());

		try (var entries = Files.list(projectRoot)) {
			assertEquals(List.of("A.java"), entries.map(path -> path.getFileName().toString()).toList());
		}
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
