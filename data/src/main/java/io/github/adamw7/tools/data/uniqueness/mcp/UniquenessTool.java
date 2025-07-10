package io.github.adamw7.tools.data.uniqueness.mcp;

import java.io.FileNotFoundException;
import java.io.UncheckedIOException;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.stereotype.Component;

import io.github.adamw7.tools.data.source.file.InMemoryCSVDataSource;
import io.github.adamw7.tools.data.source.interfaces.InMemoryDataSource;
import io.github.adamw7.tools.data.uniqueness.AbstractUniqueness;
import io.github.adamw7.tools.data.uniqueness.InMemoryUniquenessCheck;
import io.github.adamw7.tools.data.uniqueness.Result;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import io.modelcontextprotocol.spec.McpSchema.TextContent;
import io.modelcontextprotocol.spec.McpSchema.Tool;

@Component
public class UniquenessTool implements Function<Map<String, Object>, CallToolResult> {
	
	private final static Logger log = LogManager.getLogger(UniquenessTool.class.getName());

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
		log.info("Calling MCP unquieness tool for {}", arguments);
		String result = runUniqueness(arguments);

		return new CallToolResult(List.of(new TextContent(result)), false);
	}

	private String runUniqueness(Map<String, Object> arguments) {
		String fileName = String.valueOf(arguments.get("file"));
		String columnName = String.valueOf(arguments.get("columns_name"));
		int columnsRow = Integer.parseInt(String.valueOf(arguments.get("columns_row")));
		try {
			InMemoryDataSource source = new InMemoryCSVDataSource(fileName, columnsRow);
			AbstractUniqueness check = new InMemoryUniquenessCheck();
			
			source.readAll();
			
			check.setDataSource(source);
			Result result = check.exec(columnName);
			print(result, columnName);		
			return String.valueOf(result.isUnique());
		} catch (FileNotFoundException e) {
			throw new UncheckedIOException(e);
		}
	}
	
	private static void print(Result result, String column) {
		if (result.isUnique()) {
			log.info(column + " is unique");	
		} else {
			log.info(column + " is NOT unique");	
		}
	}

}
