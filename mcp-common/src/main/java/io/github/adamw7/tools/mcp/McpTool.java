package io.github.adamw7.tools.mcp;

import java.util.Map;
import java.util.function.Function;

import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import io.modelcontextprotocol.spec.McpSchema.Tool;

/**
 * A tool an MCP server exposes. Each tool carries its own {@link Tool} definition
 * (name and input schema) and maps a call's arguments to a {@link CallToolResult}.
 * Depending on this abstraction lets {@link AbstractMcpConfiguration} register any
 * number of tools without knowing their concrete types.
 */
public interface McpTool extends Function<Map<String, Object>, CallToolResult> {

	Tool getToolDefinition();
}
