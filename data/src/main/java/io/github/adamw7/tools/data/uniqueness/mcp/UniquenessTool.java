package io.github.adamw7.tools.data.uniqueness.mcp;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.List;
import java.util.Map;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import io.github.adamw7.tools.data.source.file.InMemoryCSVDataSource;
import io.github.adamw7.tools.data.source.interfaces.InMemoryDataSource;
import io.github.adamw7.tools.data.uniqueness.InMemoryUniquenessCheck;
import io.github.adamw7.tools.data.uniqueness.Result;
import io.github.adamw7.tools.data.uniqueness.Uniqueness;
import io.github.adamw7.tools.mcp.McpTool;
import io.github.adamw7.tools.mcp.ToolArguments;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import io.modelcontextprotocol.spec.McpSchema.TextContent;
import io.modelcontextprotocol.spec.McpSchema.Tool;

public class UniquenessTool implements McpTool {
	
	private static final Logger log = LogManager.getLogger(UniquenessTool.class.getName());

    private final Tool toolDefinition = Tool.builder("uniqueness_check",
                    Map.of(
                        "type", "object",
                        "properties", Map.of(
                            "file", Map.of("type", "string", "description", "filename"),
                            "columns_row", Map.of("type", "integer", "description", "number of the columns row"),
                            "columns_name", Map.of("type", "string", "description", "name of the column to check")
                        ),
                        "required", List.of("file", "columns_row", "columns_name")
                    )).description(
            "Check if a given set of columns is unique in a given data set").build();

	public UniquenessTool() {
    }

	public Tool getToolDefinition() {
		return toolDefinition;
	}

	@Override
	public CallToolResult apply(Map<String, Object> arguments) {
		log.info("Calling MCP uniqueness tool for {}", arguments);
		String result = runUniqueness(arguments);

		return CallToolResult.builder().content(List.of(TextContent.builder(result).build())).isError(false).build();
	}

	private String runUniqueness(Map<String, Object> arguments) {
		String fileName = ToolArguments.requiredString(arguments, "file");
		String columnName = ToolArguments.requiredString(arguments, "columns_name");
		int columnsRow = ToolArguments.requiredInt(arguments, "columns_row");
		try (InMemoryDataSource source = new InMemoryCSVDataSource(fileName, columnsRow)) {
			Uniqueness check = new InMemoryUniquenessCheck(source);
			Result result = check.exec(columnName);
			logResult(result, columnName);
			return String.valueOf(result.isUnique());
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}

	private static void logResult(Result result, String column) {
		log.info("{} is {}", column, result.isUnique() ? "unique" : "NOT unique");
	}

}
