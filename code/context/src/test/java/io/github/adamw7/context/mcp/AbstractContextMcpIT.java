package io.github.adamw7.context.mcp;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.test.web.server.LocalServerPort;

import io.modelcontextprotocol.client.McpClient;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.client.transport.HttpClientStreamableHttpTransport;
import io.modelcontextprotocol.spec.McpSchema;

/**
 * The fixture every context-engineering MCP integration test shares: a two-class
 * project in a temporary directory, a synchronous client connected to the server
 * the subclass's {@code @SpringBootTest} started, and the helpers that unwrap a
 * tool result. Only the transport differs between the stateless, streamable-HTTP
 * and HTTPS servers, so that is all a subclass supplies — along with the tool
 * calls it is there to assert.
 */
abstract class AbstractContextMcpIT {

	private static final ObjectMapper MAPPER = new ObjectMapper();

	@LocalServerPort
	protected int port;

	@TempDir
	protected Path projectRoot;

	protected McpSyncClient client;

	@BeforeEach
	void startClient() throws IOException {
		Files.writeString(projectRoot.resolve("A.java"), "public class A {}");
		Files.writeString(projectRoot.resolve("B.java"), "public class B { A a; }");
		client = McpClient.sync(transport())
				.clientInfo(McpSchema.Implementation.builder(clientName(), "1.0").build())
				.build();
		client.initialize();
	}

	@AfterEach
	void closeClient() {
		client.close();
	}

	/** The transport reaching the server this test started, built once {@link #port} is known. */
	protected abstract HttpClientStreamableHttpTransport transport();

	/** The name this test's client identifies itself to the server with. */
	protected abstract String clientName();

	protected McpSchema.CallToolResult call(String tool, Map<String, Object> arguments) {
		return client.callTool(McpSchema.CallToolRequest.builder(tool).arguments(arguments).build());
	}

	protected List<String> textValues(JsonNode array) {
		List<String> values = new ArrayList<>();
		array.forEach(node -> values.add(node.asText()));
		return values;
	}

	protected String singleTextResult(McpSchema.CallToolResult result) {
		assertFalse(result.isError(), () -> "unexpected error result: " + result.content());
		assertEquals(1, result.content().size(), "expected exactly one content element");
		McpSchema.Content content = result.content().getFirst();
		assertInstanceOf(McpSchema.TextContent.class, content);
		return ((McpSchema.TextContent) content).text();
	}

	protected JsonNode parse(String json) {
		try {
			return MAPPER.readTree(json);
		} catch (JsonProcessingException e) {
			throw new IllegalStateException("Invalid JSON: " + json, e);
		}
	}
}
