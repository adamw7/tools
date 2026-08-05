package io.github.adamw7.tools.data.uniqueness.mcp;

import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(
		classes = Main.class,
		webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
		properties = { "transport.mode=streamable-http", "spring.main.banner-mode=off" })
public class McpStreamableHttpIT extends AbstractUniquenessMcpIT {

	@Override
	protected String clientName() {
		return "integration-test-client";
	}
}
