package io.github.adamw7.tools.data.uniqueness.mcp;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;

import io.github.adamw7.tools.data.Utils;
import io.github.adamw7.tools.data.uniqueness.ColumnNotFoundException;
import io.github.adamw7.tools.mcp.ToolResult;

public class UniquenessToolTest {

	@Test
	public void happyPath() {
		UniquenessTool tool = new UniquenessTool();
		assertNotNull(tool.getToolDefinition());
		Map<String, Object> input = new HashMap<>();
		input.put("file", Utils.getHouseholdFile());
		input.put("columns_row", "1");
		input.put("columns_name", "year1");
		ToolResult result = tool.apply(input);
        assertFalse(result.isError());
        assertTrue("false".equals(result.text()));
	}

	@Test
	public void uniqueColumn() {
		UniquenessTool tool = new UniquenessTool();
		Map<String, Object> input = new HashMap<>();
		input.put("file", Utils.getHouseholdFile());
		input.put("columns_row", "1");
		input.put("columns_name", "income");
		ToolResult result = tool.apply(input);
		assertFalse(result.isError());
		assertTrue("true".equals(result.text()));
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

	@Test
	public void repeatedInvocationsDoNotLeakFileHandles() throws Exception {
		Path fdDir = Paths.get("/proc/self/fd");
		assumeTrue(Files.isDirectory(fdDir), "file-descriptor probing is only available on Linux");

		UniquenessTool tool = new UniquenessTool();
		warmUp(tool);

		long before = openFileDescriptors(fdDir);
		for (int i = 0; i < 60; i++) {
			invokeSuccess(tool);
			invokeMissingColumn(tool);
			invokeMissingFile(tool);
		}
		long after = openFileDescriptors(fdDir);

		// Every success and every failure path must close its data source. Before the
		// fix each call leaked at least one descriptor, so 180 calls would balloon the
		// count well past this small allowance for unrelated JVM activity.
		assertTrue(after - before <= 15, "leaked file descriptors: before=" + before + " after=" + after);
	}

	private void warmUp(UniquenessTool tool) {
		invokeSuccess(tool);
		invokeMissingColumn(tool);
		invokeMissingFile(tool);
	}

	private void invokeSuccess(UniquenessTool tool) {
		Map<String, Object> input = new HashMap<>();
		input.put("file", Utils.getHouseholdFile());
		input.put("columns_row", "1");
		input.put("columns_name", "income");
		tool.apply(input);
	}

	private void invokeMissingColumn(UniquenessTool tool) {
		Map<String, Object> input = new HashMap<>();
		input.put("file", Utils.getHouseholdFile());
		input.put("columns_row", "1");
		input.put("columns_name", "nonExistingColumn");
		assertThrows(ColumnNotFoundException.class, () -> tool.apply(input));
	}

	private void invokeMissingFile(UniquenessTool tool) {
		Map<String, Object> input = new HashMap<>();
		input.put("file", "nonExistentFile.csv");
		input.put("columns_row", "1");
		input.put("columns_name", "income");
		assertThrows(UncheckedIOException.class, () -> tool.apply(input));
	}

	private long openFileDescriptors(Path fdDir) throws IOException {
		try (Stream<Path> descriptors = Files.list(fdDir)) {
			return descriptors.count();
		}
	}
}
