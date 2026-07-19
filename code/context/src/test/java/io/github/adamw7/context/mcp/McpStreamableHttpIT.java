package io.github.adamw7.context.mcp;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;

import io.modelcontextprotocol.client.McpClient;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.client.transport.HttpClientStreamableHttpTransport;
import io.modelcontextprotocol.spec.McpSchema;

@SpringBootTest(
		classes = Main.class,
		webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
		properties = {
				"transport.mode=streamable-http",
				"spring.main.banner-mode=off",
				"context.allowed-roots=${java.io.tmpdir}" })
public class McpStreamableHttpIT {

	private static final ObjectMapper MAPPER = new ObjectMapper();

	@LocalServerPort
	private int port;

	@TempDir
	Path projectRoot;

	private McpSyncClient client;

	@BeforeEach
	void setUp() throws IOException {
		Files.writeString(projectRoot.resolve("A.java"), "public class A {}");
		Files.writeString(projectRoot.resolve("B.java"), "public class B { A a; }");
		HttpClientStreamableHttpTransport transport = HttpClientStreamableHttpTransport
				.builder("http://localhost:" + port)
				.build();
		client = McpClient.sync(transport)
				.clientInfo(McpSchema.Implementation.builder("integration-test-client", "1.0").build())
				.build();
		client.initialize();
	}

	@AfterEach
	void tearDown() {
		client.close();
	}

	@Test
	void listsAllContextTools() {
		McpSchema.ListToolsResult tools = client.listTools();

		Set<String> names = tools.tools().stream().map(McpSchema.Tool::name).collect(Collectors.toSet());
		assertEquals(Set.of("project_tree", "find_context", "estimate_tokens"), names);
	}

	@Test
	void projectTreeToolReturnsTheScannedTree() {
		McpSchema.CallToolRequest request = McpSchema.CallToolRequest.builder("project_tree")
				.arguments(Map.of("path", projectRoot.toString()))
				.build();

		McpSchema.CallToolResult result = client.callTool(request);

		JsonNode tree = parse(singleTextResult(result));
		assertEquals("directory", tree.get("type").asText());
		JsonNode children = tree.get("children");
		JsonNode dependent = childNamed(children, "B.java");
		assertEquals("file", dependent.get("type").asText());
		assertEquals(List.of("A.java"), textValues(dependent.get("dependencies")));
		assertTrue(childNamed(children, "A.java").get("dependencies").isEmpty());
	}

	@Test
	void findContextToolReturnsDependencies() {
		McpSchema.CallToolRequest request = McpSchema.CallToolRequest.builder("find_context")
				.arguments(Map.of("path", projectRoot.toString(), "class_name", "B"))
				.build();

		McpSchema.CallToolResult result = client.callTool(request);

		JsonNode dependencies = parse(singleTextResult(result));
		assertEquals(List.of("A.java"), textValues(dependencies));
	}

	@Test
	void estimateTokensToolReportsATokenBreakdown() {
		McpSchema.CallToolRequest request = McpSchema.CallToolRequest.builder("estimate_tokens")
				.arguments(Map.of("path", projectRoot.toString(), "class_name", "B"))
				.build();

		McpSchema.CallToolResult result = client.callTool(request);

		JsonNode report = parse(singleTextResult(result));
		JsonNode classes = report.get("classes");
		assertEquals(2, classes.size());
		int targetTokens = tokensForClass(classes, "B.java");
		int dependencyTokens = tokensForClass(classes, "A.java");
		assertTrue(targetTokens > 0);
		assertTrue(dependencyTokens > 0);
		assertEquals(targetTokens + dependencyTokens, report.get("total").asInt());
	}

	@Test
	void projectTreeToolHonoursTheRequestedFormat() {
		McpSchema.CallToolRequest request = McpSchema.CallToolRequest.builder("project_tree")
				.arguments(Map.of("path", projectRoot.toString(), "format", "markdown"))
				.build();

		McpSchema.CallToolResult result = client.callTool(request);

		String tree = singleTextResult(result);
		assertTrue(tree.contains("- `A.java`"));
		assertTrue(tree.contains("- `B.java`"));
		assertTrue(tree.contains("- depends on: `A.java`"));
	}

	@Test
	void findContextToolReportsAnErrorForAnUnknownClass() {
		McpSchema.CallToolRequest request = McpSchema.CallToolRequest.builder("find_context")
				.arguments(Map.of("path", projectRoot.toString(), "class_name", "Missing"))
				.build();

		McpSchema.CallToolResult result = client.callTool(request);

		assertTrue(result.isError());
		assertEquals(1, result.content().size());
		String message = ((McpSchema.TextContent) result.content().getFirst()).text();
		assertTrue(message.contains("Class not found: Missing"));
	}

	private JsonNode childNamed(JsonNode children, String name) {
		for (int index = 0; index < children.size(); index++) {
			JsonNode child = children.get(index);
			if (name.equals(child.get("name").asText())) {
				return child;
			}
		}
		throw new AssertionError("No child named " + name + " in " + children);
	}

	private int tokensForClass(JsonNode classes, String className) {
		for (int index = 0; index < classes.size(); index++) {
			JsonNode entry = classes.get(index);
			if (className.equals(entry.get("class").asText())) {
				return entry.get("tokens").asInt();
			}
		}
		throw new AssertionError("No entry for class " + className + " in " + classes);
	}

	private List<String> textValues(JsonNode array) {
		List<String> values = new java.util.ArrayList<>();
		array.forEach(node -> values.add(node.asText()));
		return values;
	}

	private String singleTextResult(McpSchema.CallToolResult result) {
		assertFalse(result.isError(), () -> "unexpected error result: " + result.content());
		assertEquals(1, result.content().size(), "expected exactly one content element");
		McpSchema.Content content = result.content().getFirst();
		assertInstanceOf(McpSchema.TextContent.class, content);
		return ((McpSchema.TextContent) content).text();
	}

	private JsonNode parse(String json) {
		try {
			return MAPPER.readTree(json);
		} catch (JsonProcessingException e) {
			throw new IllegalStateException("Invalid JSON: " + json, e);
		}
	}
}
