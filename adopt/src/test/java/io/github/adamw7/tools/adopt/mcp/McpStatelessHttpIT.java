package io.github.adamw7.tools.adopt.mcp;

import org.springframework.boot.test.context.SpringBootTest;

import io.modelcontextprotocol.client.transport.HttpClientStreamableHttpTransport;

/**
 * Integration test that serves the adoption MCP server over the stateless HTTP
 * transport. The server keeps no session between requests, yet the standard
 * streamable-HTTP client speaks the same wire protocol, so tool discovery and a real
 * adoption call must still succeed end-to-end over the {@code /mcp} endpoint.
 *
 * <p>It inherits both, and adds nothing: what differs from the streamable server is the
 * transport under the same tool, and a run that clones a repository and reports on it is
 * the strongest statement that the difference does not show. The stateless path is the
 * one where a long call could plausibly lose the client between request and response.
 */
@SpringBootTest(
		classes = Main.class,
		webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
		properties = {
				"transport.mode=stateless-http",
				"spring.main.banner-mode=off" })
public class McpStatelessHttpIT extends AbstractAdoptMcpIT {

	@Override
	protected HttpClientStreamableHttpTransport transport() {
		return HttpClientStreamableHttpTransport.builder("http://localhost:" + port).build();
	}

	@Override
	protected String clientName() {
		return "integration-test-stateless-client";
	}
}
