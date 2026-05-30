package io.github.adamw7.tools.data.uniqueness.mcp;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.UncheckedIOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import io.github.adamw7.tools.data.Utils;
import io.github.adamw7.tools.data.uniqueness.ColumnNotFoundException;
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
        Content content = result.content().getFirst();
        assertTrue(content instanceof McpSchema.TextContent);
        McpSchema.TextContent textContent = (McpSchema.TextContent) content;
        assertTrue("false".equals(textContent.text()));
	}

	@Test
	public void uniqueColumn() {
		UniquenessTool tool = new UniquenessTool();
		Map<String, Object> input = new HashMap<>();
		input.put("file", Utils.getHouseholdFile());
		input.put("columns_row", "1");
		input.put("columns_name", "income");
		CallToolResult result = tool.apply(input);
		assertFalse(result.isError());
		McpSchema.TextContent textContent = (McpSchema.TextContent) result.content().getFirst();
		assertTrue("true".equals(textContent.text()));
	}

	@Test
	public void missingFile() {
		UniquenessTool tool = new UniquenessTool();
		Map<String, Object> input = new HashMap<>();
		input.put("file", "nonExistentFile.csv");
		input.put("columns_row", "1");
		input.put("columns_name", "income");
		assertThrows(UncheckedIOException.class, () -> tool.apply(input));
	}

	@Test
	public void invalidColumn() {
		UniquenessTool tool = new UniquenessTool();
		Map<String, Object> input = new HashMap<>();
		input.put("file", Utils.getHouseholdFile());
		input.put("columns_row", "1");
		input.put("columns_name", "nonExistingColumn");
		assertThrows(ColumnNotFoundException.class, () -> tool.apply(input));
	}

	@Test
	public void toolDefinitionName() {
		UniquenessTool tool = new UniquenessTool();
		assertTrue("uniqueness_check".equals(tool.getToolDefinition().name()));
	}

	@Test
	@SuppressWarnings("unchecked")
	public void toolDefinitionRequiredFields() {
		UniquenessTool tool = new UniquenessTool();
		List<String> required = (List<String>) tool.getToolDefinition().inputSchema().get("required");
		assertTrue(required.contains("file"));
		assertTrue(required.contains("columns_row"));
		assertTrue(required.contains("columns_name"));
	}
}
