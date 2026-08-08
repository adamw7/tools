package io.github.adamw7.context.mcp;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.github.adamw7.tools.mcp.ToolResult;

public class ContextFinderToolTest extends AbstractContextToolTest {

	private ContextFinderTool tool;

	@BeforeEach
	void setUp() {
		tool = new ContextFinderTool(new PathPolicy(projectRoot.toString()));
	}

	@Override
	protected ContextTool tool() {
		return tool;
	}

	@Test
	void toolDefinitionExposesName() {
		assertEquals("find_context", tool.getToolDefinition().name());
	}

	@Test
	@SuppressWarnings("unchecked")
	void toolDefinitionRequiresClassName() {
		List<String> required = (List<String>) tool.getToolDefinition().inputSchema().get("required");
		assertTrue(required.contains("class_name"));
	}

	@Test
	void findsDirectDependencies() throws IOException {
		writeJava("A", "public class A {}");
		writeJava("B", "public class B { A a; }");

		List<String> dependencies = dependencies(tool.apply(arguments("B")));

		assertEquals(List.of("A.java"), dependencies);
	}

	@Test
	void acceptsAClassNameWithExtension() throws IOException {
		writeJava("A", "public class A {}");
		writeJava("B", "public class B { A a; }");

		List<String> dependencies = dependencies(tool.apply(arguments("B.java")));

		assertEquals(List.of("A.java"), dependencies);
	}

	@Test
	void resolvesTransitiveDependenciesAtTheGivenDepth() throws IOException {
		writeJava("A", "public class A {}");
		writeJava("B", "public class B { A a; }");
		writeJava("C", "public class C { B b; }");

		Map<String, Object> arguments = arguments("C");
		arguments.put("depth", 2);

		List<String> dependencies = dependencies(tool.apply(arguments));

		assertEquals(List.of("A.java", "B.java"), dependencies);
	}

	@Test
	void unknownClassYieldsAnErrorResult() throws IOException {
		writeJava("A", "public class A {}");

		ToolResult result = tool.apply(arguments("Missing"));

		assertTrue(result.isError());
		assertTrue(result.text().contains("Class not found: Missing"));
	}

	@Test
	void aClassWithoutDependenciesReturnsAnEmptyArray() throws IOException {
		writeJava("A", "public class A {}");

		ToolResult result = tool.apply(arguments("A"));

		assertFalse(result.isError());
		assertTrue(dependencies(result).isEmpty());
	}

	@Test
	void missingClassNameIsRejected() {
		assertThrows(IllegalArgumentException.class, () -> tool.apply(pathArgument(projectRoot)));
	}

	private Map<String, Object> arguments(String className) {
		Map<String, Object> arguments = pathArgument(projectRoot);
		arguments.put("class_name", className);
		return arguments;
	}

	private List<String> dependencies(ToolResult result) {
		try {
			JsonNode array = MAPPER.readTree(result.text());
			List<String> values = new ArrayList<>();
			array.forEach(node -> values.add(node.asText()));
			return values;
		} catch (JsonProcessingException e) {
			throw new IllegalStateException("Invalid JSON result", e);
		}
	}
}
