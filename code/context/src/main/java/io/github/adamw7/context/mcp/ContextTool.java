package io.github.adamw7.context.mcp;

import io.github.adamw7.tools.mcp.McpTool;

/**
 * A tool the context-engineering MCP server exposes. It is just an
 * {@link McpTool}; the dedicated type keeps the context tools grouped under one
 * name and lets {@link McpConfiguration} talk about them in domain terms.
 */
public interface ContextTool extends McpTool {
}
