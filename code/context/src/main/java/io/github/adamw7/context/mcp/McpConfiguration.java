package io.github.adamw7.context.mcp;

import java.util.List;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

import io.github.adamw7.tools.mcp.AbstractMcpConfiguration;
import io.github.adamw7.tools.mcp.McpTool;

/**
 * Wires the context-engineering MCP server. It exposes the project-tree,
 * context-finder and token-estimate tools, confined to the configured
 * {@code context.allowed-roots}; all transport wiring is inherited from
 * {@link AbstractMcpConfiguration}.
 */
@Configuration
public class McpConfiguration extends AbstractMcpConfiguration {

	private static final Logger log = LogManager.getLogger(McpConfiguration.class);

	@Value("${context.allowed-roots:}")
	private String allowedRoots;

	@Override
	protected String serverName() {
		return "context-engineering-server";
	}

	@Override
	protected List<McpTool> tools() {
		PathPolicy pathPolicy = new PathPolicy(allowedRoots);
		log.info("Confining MCP file access to allowed roots: {}", pathPolicy.allowedRoots());
		return List.of(new ProjectTreeTool(pathPolicy), new ContextFinderTool(pathPolicy),
				new EstimateTokensTool(pathPolicy));
	}
}
