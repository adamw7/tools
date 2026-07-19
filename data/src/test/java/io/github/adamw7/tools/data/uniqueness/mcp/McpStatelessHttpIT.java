package io.github.adamw7.tools.data.uniqueness.mcp;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

import java.util.Map;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;

import io.github.adamw7.tools.data.Utils;
import io.modelcontextprotocol.client.McpClient;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.client.transport.HttpClientStreamableHttpTransport;
import io.modelcontextprotocol.spec.McpSchema;

/**
 * Integration test that serves the uniqueness MCP server over the stateless HTTP
 * transport. The server keeps no session between requests, yet the standard
 * streamable-HTTP client speaks the same wire protocol, so a real tool call must
 * still succeed end-to-end over the {@code /mcp} endpoint.
 */
@SpringBootTest(
		classes = Main.class,
		webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
		properties = { "transport.mode=stateless-http", "spring.main.banner-mode=off" })
public class McpStatelessHttpIT {

	@LocalServerPort
	private int port;

	private McpSyncClient client;

	@BeforeEach
	void setUp() {
		HttpClientStreamableHttpTransport transport = HttpClientStreamableHttpTransport
				.builder("http://localhost:" + port)
				.build();
		client = McpClient.sync(transport)
				.clientInfo(McpSchema.Implementation.builder("integration-test-stateless-client", "1.0").build())
				.build();
		client.initialize();
	}

	@AfterEach
	void tearDown() {
		client.close();
	}

	@Test
	void nonUniqueColumnReturnsFalse() {
		McpSchema.CallToolRequest request = McpSchema.CallToolRequest.builder("uniqueness_check")
				.arguments(Map.of("file", Utils.getHouseholdFile(), "columns_row", 1, "columns_name", "year1"))
				.build();

		McpSchema.CallToolResult result = client.callTool(request);

		assertEquals("false", singleTextResult(result));
	}

	@Test
	void uniqueColumnReturnsTrue() {
		McpSchema.CallToolRequest request = McpSchema.CallToolRequest.builder("uniqueness_check")
				.arguments(Map.of("file", Utils.getHouseholdFile(), "columns_row", 1, "columns_name", "income"))
				.build();

		McpSchema.CallToolResult result = client.callTool(request);

		assertEquals("true", singleTextResult(result));
	}

	private String singleTextResult(McpSchema.CallToolResult result) {
		assertFalse(result.isError(), () -> "unexpected error result: " + result.content());
		assertEquals(1, result.content().size(), "expected exactly one content element");
		McpSchema.Content content = result.content().getFirst();
		assertInstanceOf(McpSchema.TextContent.class, content);
		return ((McpSchema.TextContent) content).text();
	}
}
