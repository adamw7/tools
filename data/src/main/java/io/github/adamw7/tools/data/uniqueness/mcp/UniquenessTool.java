package io.github.adamw7.tools.data.uniqueness.mcp;

import java.util.List;
import java.util.Map;
import java.util.function.Function;

import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import io.modelcontextprotocol.spec.McpSchema.TextContent;
import io.modelcontextprotocol.spec.McpSchema.Tool;

public class UniquenessTool implements Function<Map<String, Object>, CallToolResult> {

	private final Tool toolDefinition = new Tool("uniqueness_check",
            "Check if a given set of columns in unique in a given data set", """
                    {
                        "type": "object",
                        "properties": {
                            "file": {
                                "type": "string",
                                "description": "filename"
                            },
                              "columns_row": {
                                "type": "int",
                                "description": "number of the columns row"
                            },
                            "columns_name": {
                                "type": "string",
                                "description": "name of the column to check"
                            }
                        },
                        "required": ["file", "columns_row", "columns_name"]
                    }
                    """);

	public UniquenessTool() {
    }

	public Tool getToolDefinition() {
		return toolDefinition;
	}

	@Override
	public CallToolResult apply(Map<String, Object> arguments) {
		String result = runUniqueness(arguments);

		return new CallToolResult(List.of(new TextContent(result)), false);
	}

	private String runUniqueness(Map<String, Object> arguments) {
		return null;
	}

}
