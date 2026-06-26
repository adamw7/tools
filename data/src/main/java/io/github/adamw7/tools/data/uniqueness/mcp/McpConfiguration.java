package io.github.adamw7.tools.data.uniqueness.mcp;

import java.util.List;

import org.springframework.context.annotation.Configuration;

import io.github.adamw7.tools.mcp.AbstractMcpConfiguration;
import io.github.adamw7.tools.mcp.McpTool;

/**
 * Wires the uniqueness MCP server. It exposes a single uniqueness-check tool; all
 * transport wiring is inherited from {@link AbstractMcpConfiguration}.
 */
@Configuration
public class McpConfiguration extends AbstractMcpConfiguration {

	@Override
	protected String serverName() {
		return "custom-server";
	}

	@Override
	protected List<McpTool> tools() {
		return List.of(new UniquenessTool());
	}
}
