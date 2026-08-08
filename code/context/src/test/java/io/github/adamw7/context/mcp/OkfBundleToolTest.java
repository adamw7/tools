package io.github.adamw7.context.mcp;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class OkfBundleToolTest extends AbstractContextToolTest {

	private OkfBundleTool tool;

	@BeforeEach
	void setUp() {
		tool = new OkfBundleTool(new PathPolicy(projectRoot.toString()));
	}

	@Override
	protected ContextTool tool() {
		return tool;
	}

	@Test
	void toolDefinitionExposesName() {
		assertEquals("okf_bundle", tool.getToolDefinition().name());
	}

	@Test
	void returnsTheTargetedSpecificationVersion() throws IOException {
		writeJava("A", "public class A {}");

		JsonNode bundle = bundle(arguments());

		assertEquals("0.2", bundle.get("okf_version").asText());
	}

	@Test
	void returnsOneMarkdownDocumentPerFileBesideTheRootIndex() throws IOException {
		writeJava("A", "public class A {}");
		writeJava("B", "public class B { A a; }");

		JsonNode documents = documents(arguments());

		assertEquals(3, documents.size());
		assertTrue(documents.has("index.md"));
		assertTrue(documents.has("A.java.md"));
		assertTrue(documents.has("B.java.md"));
	}

	@Test
	void conceptDocumentsCarryFrontmatterWithAType() throws IOException {
		writeJava("A", "public class A {}");

		String document = documents(arguments()).get("A.java.md").asText();

		assertTrue(document.startsWith("---"));
		assertTrue(document.contains("type: Java Source File"));
	}

	@Test
	void linksADependencyToTheConceptThatDescribesIt() throws IOException {
		writeJava("A", "public class A {}");
		writeJava("B", "public class B { A a; }");

		JsonNode documents = documents(arguments());

		assertTrue(documents.get("B.java.md").asText().contains("[`A.java`](/A.java.md)"));
	}

	@Test
	void honoursTheRequestedLanguage() throws IOException {
		Files.writeString(projectRoot.resolve("A.kt"), "class A");

		Map<String, Object> arguments = arguments();
		arguments.put("language", "kotlin");

		assertTrue(documents(arguments).get("A.kt.md").asText().contains("type: Kotlin Source File"));
	}

	@Test
	void resultIsNotAnError() throws IOException {
		writeJava("A", "public class A {}");

		assertFalse(tool.apply(arguments()).isError());
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
		return pathArgument(projectRoot);
	}

	private JsonNode bundle(Map<String, Object> arguments) throws IOException {
		return MAPPER.readTree(tool.apply(arguments).text());
	}

	private JsonNode documents(Map<String, Object> arguments) throws IOException {
		return bundle(arguments).get("documents");
	}
}
