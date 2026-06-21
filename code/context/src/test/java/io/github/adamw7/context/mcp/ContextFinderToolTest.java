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

import org.json.JSONArray;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import io.modelcontextprotocol.spec.McpSchema.TextContent;

public class ContextFinderToolTest {

	@TempDir
	Path projectRoot;

	private final ContextFinderTool tool = new ContextFinderTool();

	@Test
	void toolDefinitionExposesName() {
		assertEquals("find_context", tool.getToolDefinition().name());
	}

	@Test
	@SuppressWarnings("unchecked")
	void toolDefinitionRequiresPathAndClassName() {
		List<String> required = (List<String>) tool.getToolDefinition().inputSchema().get("required");
		assertTrue(required.contains("path"));
		assertTrue(required.contains("class_name"));
	}

	@Test
	void findsDirectDependencies() throws IOException {
		writeJava("A", "public class A {}");
		writeJava("B", "public class B { A a; }");

		List<Object> dependencies = dependencies(tool.apply(arguments("B")));

		assertEquals(List.of("A.java"), dependencies);
	}

	@Test
	void acceptsAClassNameWithExtension() throws IOException {
		writeJava("A", "public class A {}");
		writeJava("B", "public class B { A a; }");

		List<Object> dependencies = dependencies(tool.apply(arguments("B.java")));

		assertEquals(List.of("A.java"), dependencies);
	}

	@Test
	void resolvesTransitiveDependenciesAtTheGivenDepth() throws IOException {
		writeJava("A", "public class A {}");
		writeJava("B", "public class B { A a; }");
		writeJava("C", "public class C { B b; }");

		Map<String, Object> arguments = arguments("C");
		arguments.put("depth", 2);

		List<Object> dependencies = dependencies(tool.apply(arguments));

		assertEquals(List.of("A.java", "B.java"), dependencies);
	}

	@Test
	void unknownClassYieldsAnErrorResult() throws IOException {
		writeJava("A", "public class A {}");

		CallToolResult result = tool.apply(arguments("Missing"));

		assertTrue(result.isError());
		assertTrue(text(result).contains("Class not found: Missing"));
	}

	@Test
	void aClassWithoutDependenciesReturnsAnEmptyArray() throws IOException {
		writeJava("A", "public class A {}");

		CallToolResult result = tool.apply(arguments("A"));

		assertFalse(result.isError());
		assertTrue(dependencies(result).isEmpty());
	}

	@Test
	void missingClassNameIsRejected() {
		Map<String, Object> arguments = new HashMap<>();
		arguments.put("path", projectRoot.toString());
		assertThrows(IllegalArgumentException.class, () -> tool.apply(arguments));
	}

	private Map<String, Object> arguments(String className) {
		Map<String, Object> arguments = new HashMap<>();
		arguments.put("path", projectRoot.toString());
		arguments.put("class_name", className);
		return arguments;
	}

	private void writeJava(String className, String body) throws IOException {
		Files.writeString(projectRoot.resolve(className + ".java"), body);
	}

	private List<Object> dependencies(CallToolResult result) {
		return new JSONArray(text(result)).toList();
	}

	private String text(CallToolResult result) {
		return ((TextContent) result.content().getFirst()).text();
	}
}
