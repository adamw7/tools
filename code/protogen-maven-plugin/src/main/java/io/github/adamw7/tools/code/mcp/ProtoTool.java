package io.github.adamw7.tools.code.mcp;

import java.lang.reflect.InvocationTargetException;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import com.google.protobuf.GeneratedMessageV3;

import io.github.adamw7.tools.code.gen.ClassContainer;
import io.github.adamw7.tools.code.gen.Code;
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
		ClassContainer container;
		String generatedCode = null;
		try {
			container = new Code(null, null).genBuilder(getClass(arguments)).getFirst();
			if (container != null) {
				generatedCode = container.codeAsString();
			}
		} catch (NoSuchMethodException | SecurityException | IllegalAccessException | InvocationTargetException e) {
			generatedCode = e.getMessage();
		}

		return new CallToolResult(
                List.of(new TextContent(generatedCode)),
                false
            );
	}

	private Class<? extends GeneratedMessageV3> getClass(Map<String, Object> arguments) {
		// TODO
		return null;
	}

}
