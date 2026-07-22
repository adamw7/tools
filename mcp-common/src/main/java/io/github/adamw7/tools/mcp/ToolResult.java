package io.github.adamw7.tools.mcp;

/**
 * The outcome of an {@link McpTool} call: a single block of text and whether it
 * represents an error. This is the SPI's own, transport-neutral result type, so a
 * tool never mentions the underlying MCP SDK; {@link AbstractMcpConfiguration}
 * translates it into the SDK's {@code CallToolResult} when answering the client.
 *
 * @param text    the textual payload returned to the caller
 * @param isError whether the call failed, so the client can surface it as an error
 */
public record ToolResult(String text, boolean isError) {

	/**
	 * A successful result carrying the given text.
	 */
	public static ToolResult success(String text) {
		return new ToolResult(text, false);
	}

	/**
	 * A failed result carrying the given message.
	 */
	public static ToolResult error(String message) {
		return new ToolResult(message, true);
	}
}
