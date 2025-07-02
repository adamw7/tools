package io.github.adamw7.tools.data.uniqueness.mcp;

import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;

import io.modelcontextprotocol.spec.McpSchema.CallToolResult;

import static org.junit.jupiter.api.Assertions.*;

public class UniquenessToolTest {

	@Test
	public void happyPath() {
		UniquenessTool tool = new UniquenessTool();
		assertNotNull(tool.getToolDefinition());
		Map<String, Object> input = new HashMap<>();
		input.put("file", "1.csv");
		input.put("columns_row", "1");
		input.put("columns_name", "ID");
		CallToolResult result = tool.apply(input);
        assertFalse(result.isError());
	}
}
