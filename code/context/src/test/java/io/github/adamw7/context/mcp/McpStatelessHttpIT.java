package io.github.adamw7.context.mcp;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import io.modelcontextprotocol.client.transport.HttpClientStreamableHttpTransport;
import io.modelcontextprotocol.spec.McpSchema;

/**
 * Integration test that serves the context-engineering MCP server over the
 * stateless HTTP transport. The server keeps no session between requests, yet the
 * standard streamable-HTTP client speaks the same wire protocol, so tool discovery
 * and a real tool call must still succeed end-to-end over the {@code /mcp}
 * endpoint.
 */
@SpringBootTest(
		classes = Main.class,
		webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
		properties = {
				"transport.mode=stateless-http",
				"spring.main.banner-mode=off",
				"context.allowed-roots=${java.io.tmpdir}" })
public class McpStatelessHttpIT extends AbstractContextMcpIT {

	@Override
	protected HttpClientStreamableHttpTransport transport() {
		return HttpClientStreamableHttpTransport.builder("http://localhost:" + port).build();
	}

	@Override
	protected String clientName() {
		return "integration-test-stateless-client";
	}

	@Test
	void listsAllContextTools() {
		McpSchema.ListToolsResult tools = client.listTools();

		Set<String> names = tools.tools().stream().map(McpSchema.Tool::name).collect(Collectors.toSet());
		assertEquals(Set.of("project_tree", "find_context", "estimate_tokens", "okf_bundle"), names);
	}

	@Test
	void findContextToolReturnsDependencies() {
		McpSchema.CallToolResult result = call("find_context",
				Map.of("path", projectRoot.toString(), "class_name", "B"));

		JsonNode dependencies = parse(singleTextResult(result));
		assertEquals(List.of("A.java"), textValues(dependencies));
	}
}
