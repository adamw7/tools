package io.github.adamw7.tools.mcp;

import java.util.Map;

/**
 * The definition an {@link McpTool} advertises to clients: its name, a human
 * description and the JSON schema of its input arguments. This is the SPI's own,
 * transport-neutral definition type, so a tool never mentions the underlying MCP
 * SDK; {@link AbstractMcpConfiguration} translates it into the SDK's {@code Tool}
 * when registering the tool with the server.
 *
 * @param name        the tool's unique name
 * @param description a human-readable description of what the tool does
 * @param inputSchema the JSON schema (as a nested map) describing the arguments
 */
public record ToolDefinition(String name, String description, Map<String, Object> inputSchema) {
}
