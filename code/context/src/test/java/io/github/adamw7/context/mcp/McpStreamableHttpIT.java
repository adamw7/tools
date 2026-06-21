package io.github.adamw7.context.mcp;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import org.json.JSONArray;
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
	void listsBothContextTools() {
		McpSchema.ListToolsResult tools = client.listTools();

		assertTrue(tools.tools().stream().anyMatch(tool -> tool.name().equals("project_tree")));
		assertTrue(tools.tools().stream().anyMatch(tool -> tool.name().equals("find_context")));
	}

	@Test
	void projectTreeToolReturnsTheScannedTree() {
		McpSchema.CallToolRequest request = McpSchema.CallToolRequest.builder("project_tree")
				.arguments(Map.of("path", projectRoot.toString()))
				.build();

		McpSchema.CallToolResult result = client.callTool(request);

		assertFalse(result.isError());
		String tree = ((McpSchema.TextContent) result.content().getFirst()).text();
		assertTrue(tree.contains("A.java"));
		assertTrue(tree.contains("B.java"));
	}

	@Test
	void findContextToolReturnsDependencies() {
		McpSchema.CallToolRequest request = McpSchema.CallToolRequest.builder("find_context")
				.arguments(Map.of("path", projectRoot.toString(), "class_name", "B"))
				.build();

		McpSchema.CallToolResult result = client.callTool(request);

		assertFalse(result.isError());
		String dependencies = ((McpSchema.TextContent) result.content().getFirst()).text();
		assertEquals("A.java", new JSONArray(dependencies).getString(0));
	}
}
