package io.github.adamw7.tools.adopt.mcp;

import java.util.List;

import org.springframework.context.annotation.Configuration;

import io.github.adamw7.tools.mcp.AbstractMcpConfiguration;
import io.github.adamw7.tools.mcp.McpTool;

/**
 * Wires the adoption MCP server. It exposes a single {@code adopt_repo} tool
 * that runs the adoption pipeline against the server's own
 * {@code git}/{@code claude}/{@code gh} toolchain; all transport wiring is
 * inherited from {@link AbstractMcpConfiguration}.
 */
@Configuration
public class McpConfiguration extends AbstractMcpConfiguration {

	@Override
	protected String serverName() {
		return "adopt-server";
	}

	@Override
	protected List<McpTool> tools() {
		return List.of(new AdoptTool());
	}
}
