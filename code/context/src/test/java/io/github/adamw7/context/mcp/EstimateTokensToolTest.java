package io.github.adamw7.context.mcp;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.github.adamw7.context.TokenEstimator;
import io.github.adamw7.tools.mcp.ToolResult;

public class EstimateTokensToolTest extends AbstractContextToolTest {

	private EstimateTokensTool tool;

	@BeforeEach
	void setUp() {
		tool = new EstimateTokensTool(new PathPolicy(projectRoot.toString()), oneTokenPerCharacter());
	}

	@Override
	protected ContextTool tool() {
		return tool;
	}

	@Test
	void toolDefinitionExposesName() {
		assertEquals("estimate_tokens", tool.getToolDefinition().name());
	}

	@Test
	@SuppressWarnings("unchecked")
	void toolDefinitionRequiresClassName() {
		List<String> required = (List<String>) tool.getToolDefinition().inputSchema().get("required");
		assertTrue(required.contains("class_name"));
	}

	@Test
	void reportsTheTargetAndItsDependenciesWithATotal() throws IOException {
		writeJava("A", "AA");
		writeJava("B", "A");

		JsonNode report = report(tool.apply(arguments("B")));

		assertEquals(3, report.get("total").asInt());
		assertEquals(2, report.get("classes").size());
		assertEquals("B.java", report.get("classes").get(0).get("class").asText());
		assertEquals(1, report.get("classes").get(0).get("tokens").asInt());
		assertEquals("A.java", report.get("classes").get(1).get("class").asText());
		assertEquals(2, report.get("classes").get(1).get("tokens").asInt());
	}

	@Test
	void aClassWithoutDependenciesReportsOnlyItself() throws IOException {
		writeJava("A", "AAAA");

		JsonNode report = report(tool.apply(arguments("A")));

		assertEquals(4, report.get("total").asInt());
		assertEquals(1, report.get("classes").size());
	}

	@Test
	void unknownClassYieldsAnErrorResult() throws IOException {
		writeJava("A", "A");

		ToolResult result = tool.apply(arguments("Missing"));

		assertTrue(result.isError());
		assertTrue(result.text().contains("Class not found: Missing"));
	}

	@Test
	void missingClassNameIsRejected() {
		assertThrows(IllegalArgumentException.class, () -> tool.apply(pathArgument(projectRoot)));
	}

	@Test
	void defaultsToTheSubwordEstimatorWhenNoneIsGiven() throws IOException {
		writeJava("A", "a.b");
		EstimateTokensTool defaultTool = new EstimateTokensTool(new PathPolicy(projectRoot.toString()));

		JsonNode report = report(defaultTool.apply(arguments("A")));

		assertFalse(report.get("classes").isEmpty());
		assertEquals(3, report.get("total").asInt());
	}

	@Test
	void includesTransitiveDependenciesAtTheRequestedDepth() throws IOException {
		writeJava("A", "AAAA");
		writeJava("B", "A");
		writeJava("C", "B");

		Map<String, Object> arguments = arguments("C");
		arguments.put("depth", 2);

		JsonNode report = report(tool.apply(arguments));

		assertEquals(6, report.get("total").asInt());
		assertEquals(3, report.get("classes").size());
		assertEquals("C.java", report.get("classes").get(0).get("class").asText());
		assertEquals("B.java", report.get("classes").get(1).get("class").asText());
		assertEquals("A.java", report.get("classes").get(2).get("class").asText());
	}

	@Test
	void boundsTheBreakdownToTheGivenDepth() throws IOException {
		writeJava("A", "AAAA");
		writeJava("B", "A");
		writeJava("C", "B");

		JsonNode report = report(tool.apply(arguments("C")));

		assertEquals(2, report.get("classes").size());
		assertEquals(2, report.get("total").asInt());
	}

	@Test
	void estimatesKotlinSourcesWhenRequested() throws IOException {
		Files.writeString(projectRoot.resolve("Foo.kt"), "FF");
		Files.writeString(projectRoot.resolve("Bar.kt"), "Foo");

		Map<String, Object> arguments = arguments("Bar");
		arguments.put("language", "kotlin");

		JsonNode report = report(tool.apply(arguments));

		assertEquals(5, report.get("total").asInt());
		assertEquals("Bar.kt", report.get("classes").get(0).get("class").asText());
		assertEquals("Foo.kt", report.get("classes").get(1).get("class").asText());
	}

	private TokenEstimator oneTokenPerCharacter() {
		return text -> text == null ? 0 : text.length();
	}

	private Map<String, Object> arguments(String className) {
		Map<String, Object> arguments = pathArgument(projectRoot);
		arguments.put("class_name", className);
		return arguments;
	}

	private JsonNode report(ToolResult result) {
		try {
			return MAPPER.readTree(result.text());
		} catch (JsonProcessingException e) {
			throw new IllegalStateException("Invalid JSON result", e);
		}
	}
}
