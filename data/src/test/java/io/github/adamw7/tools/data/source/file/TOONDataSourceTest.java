package io.github.adamw7.tools.data.source.file;

import static java.util.Map.entry;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import io.github.adamw7.tools.data.Utils;

public class TOONDataSourceTest {

	private static final int FILE_ROW_COUNT = 70;

	private static Map<String, String> collect(InMemoryTOONDataSource source) throws IOException {
		Map<String, String> rows = new LinkedHashMap<>();
		while (source.hasMoreData()) {
			String[] row = source.nextRow();
			if (row != null) {
				rows.put(row[0], row[1]);
			}
		}
		return rows;
	}

	@Test
	public void testGetColumnNames() throws IOException {
		try (InMemoryTOONDataSource source = new InMemoryTOONDataSource(Utils.getFileName("test.toon"))) {
			source.open();
			String[] columnNames = source.getColumnNames();
			assertNotNull(columnNames);
			assertEquals(FILE_ROW_COUNT, columnNames.length);
		}
	}

	@Test
	public void testIterateData() throws IOException {
		try (InMemoryTOONDataSource source = new InMemoryTOONDataSource(Utils.getFileName("test.toon"))) {
			source.open();
			int rowCount = 0;
			while (source.hasMoreData()) {
				String[] row = source.nextRow();
				if (row != null) {
					rowCount++;
					assertEquals(2, row.length);
					assertNotNull(row[0]);
					assertNotNull(row[1]);
				}
			}
			assertEquals(FILE_ROW_COUNT, rowCount);
		}
	}

	@Test
	public void testReadAll() throws IOException {
		try (InMemoryTOONDataSource source = new InMemoryTOONDataSource(Utils.getFileName("test.toon"))) {
			List<String[]> data = source.readAll();
			assertNotNull(data);
			assertFalse(data.isEmpty());
			for (String[] row : data) {
				assertEquals(2, row.length);
				assertNotNull(row[0]);
				assertNotNull(row[1]);
			}
		}
	}

	@Test
	public void testReset() throws IOException {
		try (InMemoryTOONDataSource source = new InMemoryTOONDataSource(Utils.getFileName("test.toon"))) {
			source.open();

			// Read all data
			while (source.hasMoreData()) {
				source.nextRow();
			}

			// Reset and verify we can read again
			source.reset();
			assertTrue(source.hasMoreData());
			String[] row = source.nextRow();
			assertNotNull(row);
		}
	}

	@Test
	public void testOpenTwiceThrowsException() throws IOException {
		try (InMemoryTOONDataSource source = new InMemoryTOONDataSource(Utils.getFileName("test.toon"))) {
			source.open();
			assertThrows(IllegalStateException.class, () -> source.open());
		}
	}

	@Test
	public void testNextRowBeforeOpenThrowsException() throws IOException {
		try (InMemoryTOONDataSource source = new InMemoryTOONDataSource(Utils.getFileName("test.toon"))) {
			assertThrows(IllegalStateException.class, () -> source.nextRow());
		}
	}

	@Test
	public void testHasMoreDataBeforeOpenThrowsException() throws IOException {
		try (InMemoryTOONDataSource source = new InMemoryTOONDataSource(Utils.getFileName("test.toon"))) {
			assertThrows(IllegalStateException.class, () -> source.hasMoreData());
		}
	}

	@Test
	public void testPrimitiveArray() throws IOException {
		String toon = "tags[3]: admin,ops,dev";
		try (InMemoryTOONDataSource source = new InMemoryTOONDataSource(
				new ByteArrayInputStream(toon.getBytes(StandardCharsets.UTF_8)))) {
			source.open();

			assertTrue(source.getColumnNames().length > 0);
			assertEquals(Map.of("tags", "3", "tags[0]", "admin", "tags[1]", "ops", "tags[2]", "dev"), collect(source));
		}
	}

	@Test
	public void testTabularArray() throws IOException {
		String toon = "users[2]{id,name,role}:\n  1,Alice,admin\n  2,Bob,user";
		try (InMemoryTOONDataSource source = new InMemoryTOONDataSource(
				new ByteArrayInputStream(toon.getBytes(StandardCharsets.UTF_8)))) {
			source.open();

			assertEquals(Map.ofEntries(entry("id", "id"), entry("name", "name"), entry("role", "role"),
					entry("users", "2"), entry("users[0].id", "1"), entry("users[0].name", "Alice"),
					entry("users[0].role", "admin"), entry("users[1].id", "2"), entry("users[1].name", "Bob"),
					entry("users[1].role", "user")), collect(source));
		}
	}

	@Test
	public void testNestedObject() throws IOException {
		String toon = "context:\n  task: Our favorite hikes\n  location: Boulder";
		try (InMemoryTOONDataSource source = new InMemoryTOONDataSource(
				new ByteArrayInputStream(toon.getBytes(StandardCharsets.UTF_8)))) {
			source.open();

			assertEquals(Map.of("context.task", "Our favorite hikes", "context.location", "Boulder"), collect(source));
		}
	}

	@Test
	public void testQuotedStrings() throws IOException {
		String toon = "message: \"Hello, World!\"";
		try (InMemoryTOONDataSource source = new InMemoryTOONDataSource(
				new ByteArrayInputStream(toon.getBytes(StandardCharsets.UTF_8)))) {
			source.open();

			assertEquals(Map.of("message", "Hello, World!"), collect(source));
		}
	}

	@Test
	public void testIterator() throws IOException {
		try (InMemoryTOONDataSource source = new InMemoryTOONDataSource(Utils.getFileName("test.toon"))) {
			source.open();

			var iterator = source.iterator();
			assertNotNull(iterator);
			assertTrue(iterator.hasNext());

			int count = 0;
			while (iterator.hasNext()) {
				String[] row = iterator.next();
				assertNotNull(row);
				count++;
			}
			assertEquals(FILE_ROW_COUNT, count);
		}
	}

	@Test
	public void testSimpleKeyValuePairs() throws IOException {
		try (InMemoryTOONDataSource source = new InMemoryTOONDataSource(Utils.getFileName("test.toon"))) {
			source.open();

			Map<String, String> rows = collect(source);

			assertEquals(FILE_ROW_COUNT, rows.size());
			assertEquals("MyTestApp", rows.get("appName"));
			assertEquals("1.2.3", rows.get("version"));
			assertEquals("true", rows.get("isEnabled"));
			assertEquals("false", rows.get("isDisabled"));
			assertEquals("5", rows.get("maxRetries"));
			assertEquals("3.14159", rows.get("pi"));
		}
	}

	@Test
	public void testNestedObjectFromFile() throws IOException {
		try (InMemoryTOONDataSource source = new InMemoryTOONDataSource(Utils.getFileName("test.toon"))) {
			source.open();

			Map<String, String> rows = collect(source);

			assertEquals(FILE_ROW_COUNT, rows.size());
			assertEquals("localhost", rows.get("database.host"));
			assertEquals("5432", rows.get("database.port"));
			assertEquals("admin", rows.get("database.credentials.username"));
			assertEquals("secret123", rows.get("database.credentials.password"));
		}
	}

	@Test
	public void testQuotedStringsFromFile() throws IOException {
		try (InMemoryTOONDataSource source = new InMemoryTOONDataSource(Utils.getFileName("test.toon"))) {
			source.open();

			Map<String, String> rows = collect(source);

			assertEquals(FILE_ROW_COUNT, rows.size());
			assertEquals("Hello, World!", rows.get("greeting"));
			assertEquals("C:\\Program Files\\App", rows.get("pathWithSpaces"));
			assertEquals("She said \"Hello\" to me", rows.get("quotedValue"));
		}
	}

	@Test
	public void testEscapeSequences() throws IOException {
		try (InMemoryTOONDataSource source = new InMemoryTOONDataSource(Utils.getFileName("test.toon"))) {
			source.open();

			Map<String, String> rows = collect(source);

			assertEquals(FILE_ROW_COUNT, rows.size());
			assertEquals("Line1\nLine2\nLine3", rows.get("multilineHint"));
			assertEquals("col1\tcol2\tcol3", rows.get("tabSeparated"));
		}
	}

	@Test
	public void testNestedArrayFromFile() throws IOException {
		try (InMemoryTOONDataSource source = new InMemoryTOONDataSource(Utils.getFileName("test.toon"))) {
			source.open();

			Map<String, String> rows = collect(source);

			assertEquals(FILE_ROW_COUNT, rows.size());
			assertEquals("3", rows.get("priorities"));
			assertEquals("high", rows.get("priorities[0]"));
			assertEquals("medium", rows.get("priorities[1]"));
			assertEquals("low", rows.get("priorities[2]"));
		}
	}

	@Test
	public void testTabularArrayWithQuotedValues() throws IOException {
		try (InMemoryTOONDataSource source = new InMemoryTOONDataSource(Utils.getFileName("test.toon"))) {
			source.open();

			Map<String, String> rows = collect(source);

			assertEquals(FILE_ROW_COUNT, rows.size());
			assertEquals("SKU001", rows.get("products[0].sku"));
			assertEquals("Widget Pro", rows.get("products[0].name"));
			assertEquals("29.99", rows.get("products[0].price"));
			assertEquals("A great widget, really!", rows.get("products[0].description"));
			assertEquals("SKU002", rows.get("products[1].sku"));
			assertEquals("Gadget Plus", rows.get("products[1].name"));
		}
	}

	@Test
	public void testNumericValuesInTabularArray() throws IOException {
		try (InMemoryTOONDataSource source = new InMemoryTOONDataSource(Utils.getFileName("test.toon"))) {
			source.open();

			Map<String, String> rows = collect(source);

			assertEquals(FILE_ROW_COUNT, rows.size());
			assertEquals("temperature", rows.get("metrics[0].name"));
			assertEquals("72.5", rows.get("metrics[0].value"));
			assertEquals("humidity", rows.get("metrics[1].name"));
			assertEquals("pressure", rows.get("metrics[2].name"));
			assertEquals("1013.25", rows.get("metrics[2].value"));
		}
	}

	@Test
	public void testBooleanValues() throws IOException {
		String toon = "enabled: true\ndisabled: false";
		try (InMemoryTOONDataSource source = new InMemoryTOONDataSource(
				new ByteArrayInputStream(toon.getBytes(StandardCharsets.UTF_8)))) {
			source.open();

			assertEquals(Map.of("enabled", "true", "disabled", "false"), collect(source));
		}
	}

	@Test
	public void testIntegerValues() throws IOException {
		String toon = "count: 42\nnegative: -10\nzero: 0";
		try (InMemoryTOONDataSource source = new InMemoryTOONDataSource(
				new ByteArrayInputStream(toon.getBytes(StandardCharsets.UTF_8)))) {
			source.open();

			assertEquals(Map.of("count", "42", "negative", "-10", "zero", "0"), collect(source));
		}
	}

	@Test
	public void testDecimalValues() throws IOException {
		String toon = "pi: 3.14159\nsmall: 0.001\nlarge: 12345.6789";
		try (InMemoryTOONDataSource source = new InMemoryTOONDataSource(
				new ByteArrayInputStream(toon.getBytes(StandardCharsets.UTF_8)))) {
			source.open();

			assertEquals(Map.of("pi", "3.14159", "small", "0.001", "large", "12345.6789"), collect(source));
		}
	}

	@Test
	public void testDeeplyNestedObject() throws IOException {
		String toon = "level1:\n  level2:\n    level3:\n      value: deep";
		try (InMemoryTOONDataSource source = new InMemoryTOONDataSource(
				new ByteArrayInputStream(toon.getBytes(StandardCharsets.UTF_8)))) {
			source.open();

			assertEquals(Map.of("level1.level2.level3.value", "deep"), collect(source));
		}
	}
}
