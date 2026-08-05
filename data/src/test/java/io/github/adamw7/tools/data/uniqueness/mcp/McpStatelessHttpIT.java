package io.github.adamw7.tools.data.uniqueness.mcp;

import org.springframework.boot.test.context.SpringBootTest;

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
public class McpStatelessHttpIT extends AbstractUniquenessMcpIT {

	@Override
	protected String clientName() {
		return "integration-test-stateless-client";
	}
}
