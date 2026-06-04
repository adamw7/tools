package io.github.adamw7.tools.data.source.file;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;

import io.github.adamw7.tools.data.Utils;
import io.github.adamw7.tools.data.source.interfaces.IterableDataSource;

public class IterableTOONDataSourceTest {

	@Test
	public void streamsSameRowsAsInMemorySource() throws IOException {
		Map<String, String> inMemory = collect(new InMemoryTOONDataSource(Utils.getFileName("test.toon")));
		Map<String, String> iterated = collect(new IterableTOONDataSource(Utils.getFileName("test.toon")));
		assertEquals(inMemory, iterated);
		assertEquals(70, iterated.size());
	}

	@Test
	public void readsSimpleKeyValuePairs() throws IOException {
		Map<String, String> data = collect(new IterableTOONDataSource(Utils.getFileName("test.toon")));
		assertEquals("MyTestApp", data.get("appName"));
		assertEquals("1.2.3", data.get("version"));
		assertEquals("true", data.get("isEnabled"));
		assertEquals("false", data.get("isDisabled"));
		assertEquals("5", data.get("maxRetries"));
		assertEquals("3.14159", data.get("pi"));
	}

	@Test
	public void readsNestedObjects() throws IOException {
		Map<String, String> data = collect(new IterableTOONDataSource(Utils.getFileName("test.toon")));
		assertEquals("localhost", data.get("database.host"));
		assertEquals("5432", data.get("database.port"));
		assertEquals("admin", data.get("database.credentials.username"));
		assertEquals("secret123", data.get("database.credentials.password"));
	}

	@Test
	public void readsTabularArray() throws IOException {
		Map<String, String> data = collect(new IterableTOONDataSource(Utils.getFileName("test.toon")));
		assertEquals("Alice", data.get("people[0].name"));
		assertEquals("30", data.get("people[0].age"));
		assertEquals("New York", data.get("people[0].city"));
		assertEquals("Bob", data.get("people[1].name"));
		assertEquals("Widget Pro", data.get("products[0].name"));
		assertEquals("A great widget, really!", data.get("products[0].description"));
	}

	@Test
	public void readsNestedArray() throws IOException {
		Map<String, String> data = collect(new IterableTOONDataSource(Utils.getFileName("test.toon")));
		assertEquals("3", data.get("priorities"));
		assertEquals("high", data.get("priorities[0]"));
		assertEquals("medium", data.get("priorities[1]"));
		assertEquals("low", data.get("priorities[2]"));
	}

	@Test
	public void readsInlinePrimitiveArray() throws IOException {
		Map<String, String> data = collect(fromString("tags[3]: admin,ops,dev"));
		assertEquals("3", data.get("tags"));
		assertEquals("admin", data.get("tags[0]"));
		assertEquals("ops", data.get("tags[1]"));
		assertEquals("dev", data.get("tags[2]"));
	}

	@Test
	public void unescapesQuotedValues() throws IOException {
		Map<String, String> data = collect(new IterableTOONDataSource(Utils.getFileName("test.toon")));
		assertEquals("Hello, World!", data.get("greeting"));
		assertEquals("C:\\Program Files\\App", data.get("pathWithSpaces"));
		assertEquals("Line1\nLine2\nLine3", data.get("multilineHint"));
		assertEquals("col1\tcol2\tcol3", data.get("tabSeparated"));
		assertEquals("She said \"Hello\" to me", data.get("quotedValue"));
	}

	@Test
	public void resetRestartsTheIteration() throws IOException {
		try (IterableTOONDataSource source = new IterableTOONDataSource(Utils.getFileName("test.toon"))) {
			source.open();
			int first = drain(source);
			source.reset();
			int second = drain(source);
			assertEquals(first, second);
		}
	}

	@Test
	public void columnNamesAreUnknownWhileIterating() throws IOException {
		try (IterableTOONDataSource source = new IterableTOONDataSource(Utils.getFileName("test.toon"))) {
			source.open();
			assertNull(source.getColumnNames());
		}
	}

	@Test
	public void openTwiceThrows() throws IOException {
		try (IterableTOONDataSource source = new IterableTOONDataSource(Utils.getFileName("test.toon"))) {
			source.open();
			assertThrows(IllegalStateException.class, source::open);
		}
	}

	@Test
	public void nextRowBeforeOpenThrows() throws IOException {
		try (IterableTOONDataSource source = new IterableTOONDataSource(Utils.getFileName("test.toon"))) {
			assertThrows(IllegalStateException.class, source::nextRow);
		}
	}

	private IterableTOONDataSource fromString(String toon) {
		return new IterableTOONDataSource(new ByteArrayInputStream(toon.getBytes(StandardCharsets.UTF_8)));
	}

	private int drain(IterableDataSource source) {
		int rows = 0;
		while (source.hasMoreData()) {
			if (source.nextRow() != null) {
				rows++;
			}
		}
		return rows;
	}

	private Map<String, String> collect(IterableDataSource source) throws IOException {
		try (source) {
			source.open();
			Map<String, String> map = new HashMap<>();
			while (source.hasMoreData()) {
				String[] row = source.nextRow();
				if (row != null) {
					map.put(row[0], row[1]);
				}
			}
			return map;
		}
	}
}
