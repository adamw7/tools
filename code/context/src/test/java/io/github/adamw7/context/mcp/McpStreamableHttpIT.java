package io.github.adamw7.context.mcp;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import io.modelcontextprotocol.client.transport.HttpClientStreamableHttpTransport;
import io.modelcontextprotocol.spec.McpSchema;

@SpringBootTest(
		classes = Main.class,
		webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
		properties = {
				"transport.mode=streamable-http",
				"spring.main.banner-mode=off",
				"context.allowed-roots=${java.io.tmpdir}" })
public class McpStreamableHttpIT extends AbstractContextMcpIT {

	@Override
	protected HttpClientStreamableHttpTransport transport() {
		return HttpClientStreamableHttpTransport.builder("http://localhost:" + port).build();
	}

	@Override
	protected String clientName() {
		return "integration-test-client";
	}

	@Test
	void listsAllContextTools() {
		McpSchema.ListToolsResult tools = client.listTools();

		Set<String> names = tools.tools().stream().map(McpSchema.Tool::name).collect(Collectors.toSet());
		assertEquals(Set.of("project_tree", "find_context", "estimate_tokens", "okf_bundle"), names);
	}

	@Test
	void projectTreeToolReturnsTheScannedTree() {
		McpSchema.CallToolResult result = call("project_tree", Map.of("path", projectRoot.toString()));

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
		McpSchema.CallToolResult result = call("find_context",
				Map.of("path", projectRoot.toString(), "class_name", "B"));

		JsonNode dependencies = parse(singleTextResult(result));
		assertEquals(List.of("A.java"), textValues(dependencies));
	}

	@Test
	void estimateTokensToolReportsATokenBreakdown() {
		McpSchema.CallToolResult result = call("estimate_tokens",
				Map.of("path", projectRoot.toString(), "class_name", "B"));

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
		McpSchema.CallToolResult result = call("project_tree",
				Map.of("path", projectRoot.toString(), "format", "markdown"));

		String tree = singleTextResult(result);
		assertTrue(tree.contains("- `A.java`"));
		assertTrue(tree.contains("- `B.java`"));
		assertTrue(tree.contains("- depends on: `A.java`"));
	}

	@Test
	void okfBundleToolReturnsAConformantBundle() {
		McpSchema.CallToolResult result = call("okf_bundle", Map.of("path", projectRoot.toString()));

		JsonNode bundle = parse(singleTextResult(result));
		assertEquals("0.2", bundle.get("okf_version").asText());
		JsonNode documents = bundle.get("documents");
		assertTrue(documents.get("index.md").asText().contains("okf_version: \"0.2\""));
		assertTrue(documents.get("A.java.md").asText().contains("type: Java Source File"));
		assertTrue(documents.get("B.java.md").asText().contains("[`A.java`](/A.java.md)"));
	}

	@Test
	void findContextToolReportsAnErrorForAnUnknownClass() {
		McpSchema.CallToolResult result = call("find_context",
				Map.of("path", projectRoot.toString(), "class_name", "Missing"));

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
}
