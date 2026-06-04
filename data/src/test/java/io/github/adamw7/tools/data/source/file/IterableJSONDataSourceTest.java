package io.github.adamw7.tools.data.source.file;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;

import io.github.adamw7.tools.data.Utils;
import io.github.adamw7.tools.data.source.interfaces.IterableDataSource;

public class IterableJSONDataSourceTest {

	@Test
	public void flattensNestedValues() throws IOException {
		Map<String, String> data = collect(new IterableJSONDataSource(Utils.getFileName("test.json")));
		assertEquals(17, data.size());
		assertEquals("Alice", data.get("people[0].name"));
		assertEquals("30", data.get("people[0].age"));
		assertEquals("New York", data.get("people[0].address.city"));
		assertEquals("NY", data.get("people[0].address.state"));
		assertEquals("Bob", data.get("people[1].name"));
		assertEquals("Los Angeles", data.get("people[1].address.city"));
		assertEquals("Toyota", data.get("cars[0].manufacturer"));
		assertEquals("Camry", data.get("cars[0].model"));
		assertEquals("2020", data.get("cars[0].year"));
		assertEquals("apple", data.get("fruits[0]"));
		assertEquals("banana", data.get("fruits[1]"));
		assertEquals("orange", data.get("fruits[2]"));
	}

	@Test
	public void readsGzippedFile() throws IOException {
		Map<String, String> data = collect(new IterableJSONDataSource(Utils.getFileName("test.json.gz")));
		assertEquals(17, data.size());
		assertEquals("Alice", data.get("people[0].name"));
	}

	@Test
	public void readsFromInputStream() throws IOException {
		try (InputStream is = getClass().getClassLoader().getResourceAsStream("test.json");
				IterableJSONDataSource source = new IterableJSONDataSource(is)) {
			source.open();
			assertEquals(17, drain(source));
		}
	}

	@Test
	public void resetRestartsTheIteration() throws IOException {
		try (IterableJSONDataSource source = new IterableJSONDataSource(Utils.getFileName("test.json"))) {
			source.open();
			int first = drain(source);
			source.reset();
			int second = drain(source);
			assertEquals(first, second);
		}
	}

	@Test
	public void columnNamesAreUnknownWhileIterating() throws IOException {
		try (IterableJSONDataSource source = new IterableJSONDataSource(Utils.getFileName("test.json"))) {
			source.open();
			assertNull(source.getColumnNames());
		}
	}

	@Test
	public void openTwiceThrows() throws IOException {
		try (IterableJSONDataSource source = new IterableJSONDataSource(Utils.getFileName("test.json"))) {
			source.open();
			assertThrows(IllegalStateException.class, source::open);
		}
	}

	@Test
	public void nextRowBeforeOpenThrows() throws IOException {
		try (IterableJSONDataSource source = new IterableJSONDataSource(Utils.getFileName("test.json"))) {
			assertThrows(IllegalStateException.class, source::nextRow);
		}
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
