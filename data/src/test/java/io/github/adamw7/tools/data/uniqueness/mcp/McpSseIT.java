package io.github.adamw7.tools.data.uniqueness.mcp;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.util.Map;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;

import io.github.adamw7.tools.data.Utils;
import io.modelcontextprotocol.client.McpClient;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.client.transport.HttpClientSseClientTransport;
import io.modelcontextprotocol.spec.McpSchema;

@SpringBootTest(
		classes = Main.class,
		webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
		properties = { "transport.mode=sse", "spring.main.banner-mode=off" })
public class McpSseIT {

	@LocalServerPort
	private int port;

	private McpSyncClient client;

	@BeforeEach
	void setUp() {
		HttpClientSseClientTransport transport = HttpClientSseClientTransport
				.builder("http://localhost:" + port)
				.sseEndpoint("/sse")
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
	void nonUniqueColumnReturnsFalse() {
		McpSchema.CallToolRequest request = McpSchema.CallToolRequest.builder("uniqueness_check")
				.arguments(Map.of("file", Utils.getHouseholdFile(), "columns_row", 1, "columns_name", "year1"))
				.build();

		McpSchema.CallToolResult result = client.callTool(request);

		assertFalse(result.isError());
		McpSchema.TextContent content = (McpSchema.TextContent) result.content().getFirst();
		assertEquals("false", content.text());
	}

	@Test
	void uniqueColumnReturnsTrue() {
		McpSchema.CallToolRequest request = McpSchema.CallToolRequest.builder("uniqueness_check")
				.arguments(Map.of("file", Utils.getHouseholdFile(), "columns_row", 1, "columns_name", "income"))
				.build();

		McpSchema.CallToolResult result = client.callTool(request);

		assertFalse(result.isError());
		McpSchema.TextContent content = (McpSchema.TextContent) result.content().getFirst();
		assertEquals("true", content.text());
	}
}
