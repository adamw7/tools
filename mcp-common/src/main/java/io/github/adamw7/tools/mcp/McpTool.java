package io.github.adamw7.tools.mcp;

import java.util.Map;
import java.util.function.Function;

/**
 * A tool an MCP server exposes. Each tool carries its own {@link ToolDefinition}
 * (name, description and input schema) and maps a call's arguments to a
 * {@link ToolResult}. Both are the SPI's own, transport-neutral types, so a tool
 * implementation never depends on the MCP SDK; {@link AbstractMcpConfiguration}
 * adapts them to the SDK when wiring the server. Depending on this abstraction
 * lets {@link AbstractMcpConfiguration} register any number of tools without
 * knowing their concrete types.
 */
public interface McpTool extends Function<Map<String, Object>, ToolResult> {

	ToolDefinition getToolDefinition();
}
