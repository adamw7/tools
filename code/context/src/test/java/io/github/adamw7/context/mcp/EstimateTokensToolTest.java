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

import org.json.JSONObject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import io.github.adamw7.context.TokenEstimator;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import io.modelcontextprotocol.spec.McpSchema.TextContent;

public class EstimateTokensToolTest {

	@TempDir
	Path projectRoot;

	@TempDir
	Path outsideRoot;

	private EstimateTokensTool tool;

	@BeforeEach
	void setUp() {
		tool = new EstimateTokensTool(new PathPolicy(projectRoot.toString()), oneTokenPerCharacter());
	}

	@Test
	void toolDefinitionExposesName() {
		assertEquals("estimate_tokens", tool.getToolDefinition().name());
	}

	@Test
	@SuppressWarnings("unchecked")
	void toolDefinitionRequiresPathAndClassName() {
		List<String> required = (List<String>) tool.getToolDefinition().inputSchema().get("required");
		assertTrue(required.contains("path"));
		assertTrue(required.contains("class_name"));
	}

	@Test
	void reportsTheTargetAndItsDependenciesWithATotal() throws IOException {
		writeJava("A", "AA");
		writeJava("B", "A");

		JSONObject report = report(tool.apply(arguments("B")));

		assertEquals(3, report.getInt("total"));
		assertEquals(2, report.getJSONArray("classes").length());
		assertEquals("B.java", report.getJSONArray("classes").getJSONObject(0).getString("class"));
		assertEquals(1, report.getJSONArray("classes").getJSONObject(0).getInt("tokens"));
		assertEquals("A.java", report.getJSONArray("classes").getJSONObject(1).getString("class"));
		assertEquals(2, report.getJSONArray("classes").getJSONObject(1).getInt("tokens"));
	}

	@Test
	void aClassWithoutDependenciesReportsOnlyItself() throws IOException {
		writeJava("A", "AAAA");

		JSONObject report = report(tool.apply(arguments("A")));

		assertEquals(4, report.getInt("total"));
		assertEquals(1, report.getJSONArray("classes").length());
	}

	@Test
	void unknownClassYieldsAnErrorResult() throws IOException {
		writeJava("A", "A");

		CallToolResult result = tool.apply(arguments("Missing"));

		assertTrue(result.isError());
		assertTrue(text(result).contains("Class not found: Missing"));
	}

	@Test
	void missingClassNameIsRejected() {
		Map<String, Object> arguments = new HashMap<>();
		arguments.put("path", projectRoot.toString());
		assertThrows(IllegalArgumentException.class, () -> tool.apply(arguments));
	}

	@Test
	void pathOutsideTheAllowedRootIsRejected() {
		Map<String, Object> arguments = arguments("A");
		arguments.put("path", outsideRoot.toString());
		assertThrows(SecurityException.class, () -> tool.apply(arguments));
	}

	@Test
	void excessiveDepthIsRejected() throws IOException {
		writeJava("A", "A");
		Map<String, Object> arguments = arguments("A");
		arguments.put("depth", 99);
		assertThrows(IllegalArgumentException.class, () -> tool.apply(arguments));
	}

	@Test
	void defaultsToTheSubwordEstimatorWhenNoneIsGiven() throws IOException {
		writeJava("A", "a.b");
		EstimateTokensTool defaultTool = new EstimateTokensTool(new PathPolicy(projectRoot.toString()));

		JSONObject report = report(defaultTool.apply(arguments("A")));

		assertFalse(report.getJSONArray("classes").isEmpty());
		assertEquals(3, report.getInt("total"));
	}

	@Test
	void includesTransitiveDependenciesAtTheRequestedDepth() throws IOException {
		writeJava("A", "AAAA");
		writeJava("B", "A");
		writeJava("C", "B");

		Map<String, Object> arguments = arguments("C");
		arguments.put("depth", 2);

		JSONObject report = report(tool.apply(arguments));

		assertEquals(6, report.getInt("total"));
		assertEquals(3, report.getJSONArray("classes").length());
		assertEquals("C.java", report.getJSONArray("classes").getJSONObject(0).getString("class"));
		assertEquals("B.java", report.getJSONArray("classes").getJSONObject(1).getString("class"));
		assertEquals("A.java", report.getJSONArray("classes").getJSONObject(2).getString("class"));
	}

	@Test
	void boundsTheBreakdownToTheGivenDepth() throws IOException {
		writeJava("A", "AAAA");
		writeJava("B", "A");
		writeJava("C", "B");

		JSONObject report = report(tool.apply(arguments("C")));

		assertEquals(2, report.getJSONArray("classes").length());
		assertEquals(2, report.getInt("total"));
	}

	@Test
	void estimatesKotlinSourcesWhenRequested() throws IOException {
		Files.writeString(projectRoot.resolve("Foo.kt"), "FF");
		Files.writeString(projectRoot.resolve("Bar.kt"), "Foo");

		Map<String, Object> arguments = arguments("Bar");
		arguments.put("language", "kotlin");

		JSONObject report = report(tool.apply(arguments));

		assertEquals(5, report.getInt("total"));
		assertEquals("Bar.kt", report.getJSONArray("classes").getJSONObject(0).getString("class"));
		assertEquals("Foo.kt", report.getJSONArray("classes").getJSONObject(1).getString("class"));
	}

	private TokenEstimator oneTokenPerCharacter() {
		return text -> text == null ? 0 : text.length();
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

	private JSONObject report(CallToolResult result) {
		return new JSONObject(text(result));
	}

	private String text(CallToolResult result) {
		return ((TextContent) result.content().getFirst()).text();
	}
}
