package io.github.adamw7.tools.data.mcp;

import java.util.List;
import java.util.Map;
import java.util.function.Function;

import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import io.modelcontextprotocol.spec.McpSchema.TextContent;
import io.modelcontextprotocol.spec.McpSchema.Tool;

public class ProtoTool implements Function<Map<String, Object>, CallToolResult> {

	private final Tool toolDefinition;

	public ProtoTool() {
		this.toolDefinition = new Tool("generate_java_from_proto", "Generate java code from proto definition", """
				{
				    "type": "object",
				    "properties": {
				        "proto": {
				            "type": "string",
				            "description": "The proto file content"
				        }
				    },
				    "required": ["proto"]
				}
				""");
	}

	public Tool getToolDefinition() {
		return toolDefinition;
	}

	@Override
	public CallToolResult apply(Map<String, Object> arguments) {
		String result = runUniqueness(arguments);

		return new CallToolResult(
                List.of(new TextContent(result)),
                false
            );
	}

	private String runUniqueness(Map<String, Object> arguments) {
		return null;
	}



}
