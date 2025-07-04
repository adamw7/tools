package io.github.adamw7.tools.data.uniqueness.mcp;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;

import io.github.adamw7.tools.data.Utils;
import io.modelcontextprotocol.spec.McpSchema;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import io.modelcontextprotocol.spec.McpSchema.Content;

public class UniquenessToolTest {

	@Test
	public void happyPath() {
		UniquenessTool tool = new UniquenessTool();
		assertNotNull(tool.getToolDefinition());
		Map<String, Object> input = new HashMap<>();
		input.put("file", Utils.getHouseholdFile());
		input.put("columns_row", "1");
		input.put("columns_name", "year1");
		CallToolResult result = tool.apply(input);
        assertFalse(result.isError());
        Content content = result.content().get(0);
        assertTrue(content instanceof McpSchema.TextContent);
        McpSchema.TextContent textContent = (McpSchema.TextContent) content;
        assertTrue("false".equals(textContent.text()));
	}
}
