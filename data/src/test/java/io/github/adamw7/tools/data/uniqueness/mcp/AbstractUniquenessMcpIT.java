package io.github.adamw7.tools.data.uniqueness.mcp;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

import java.util.Map;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.web.server.LocalServerPort;

import io.github.adamw7.tools.data.Utils;
import io.modelcontextprotocol.client.McpClient;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.client.transport.HttpClientStreamableHttpTransport;
import io.modelcontextprotocol.spec.McpSchema;

/**
 * What every uniqueness MCP integration test asserts, whichever HTTP transport the
 * subclass's {@code @SpringBootTest} started the server with: a synchronous client
 * reaches the {@code /mcp} endpoint and {@code uniqueness_check} answers correctly
 * for a column that is a key and one that is not. The transport differs only in
 * the client name it announces, so that is all a subclass supplies.
 */
abstract class AbstractUniquenessMcpIT {

	@LocalServerPort
	private int port;

	private McpSyncClient client;

	@BeforeEach
	void startClient() {
		HttpClientStreamableHttpTransport transport = HttpClientStreamableHttpTransport
				.builder("http://localhost:" + port)
				.build();
		client = McpClient.sync(transport)
				.clientInfo(McpSchema.Implementation.builder(clientName(), "1.0").build())
				.build();
		client.initialize();
	}

	@AfterEach
	void closeClient() {
		client.close();
	}

	/** The name this test's client identifies itself to the server with. */
	protected abstract String clientName();

	@Test
	void nonUniqueColumnReturnsFalse() {
		assertEquals("false", singleTextResult(uniquenessOf("year1")));
	}

	@Test
	void uniqueColumnReturnsTrue() {
		assertEquals("true", singleTextResult(uniquenessOf("income")));
	}

	private McpSchema.CallToolResult uniquenessOf(String column) {
		return client.callTool(McpSchema.CallToolRequest.builder("uniqueness_check")
				.arguments(Map.of("file", Utils.getHouseholdFile(), "columns_row", 1, "columns_name", column))
				.build());
	}

	private String singleTextResult(McpSchema.CallToolResult result) {
		assertFalse(result.isError(), () -> "unexpected error result: " + result.content());
		assertEquals(1, result.content().size(), "expected exactly one content element");
		McpSchema.Content content = result.content().getFirst();
		assertInstanceOf(McpSchema.TextContent.class, content);
		return ((McpSchema.TextContent) content).text();
	}
}
