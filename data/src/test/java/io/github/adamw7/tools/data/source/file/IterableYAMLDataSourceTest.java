package io.github.adamw7.tools.data.source.file;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;

import io.github.adamw7.tools.data.Utils;
import io.github.adamw7.tools.data.source.interfaces.IterableDataSource;

public class IterableYAMLDataSourceTest {

	@Test
	public void streamsSameRowsAsInMemorySource() throws IOException {
		Map<String, String> inMemory = collect(new InMemoryYAMLDataSource(Utils.getFileName("test.yaml")));
		Map<String, String> iterated = collect(new IterableYAMLDataSource(Utils.getFileName("test.yaml")));
		assertEquals(inMemory, iterated);
	}

	@Test
	public void flattensNestedValues() throws IOException {
		Map<String, String> data = collect(new IterableYAMLDataSource(Utils.getFileName("test.yaml")));
		assertEquals(17, data.size());
		assertEquals("Alice", data.get("people[0].name"));
		assertEquals("30", data.get("people[0].age"));
		assertEquals("New York", data.get("people[0].address.city"));
		assertEquals("NY", data.get("people[0].address.state"));
		assertEquals("Bob", data.get("people[1].name"));
		assertEquals("Toyota", data.get("cars[0].manufacturer"));
		assertEquals("2020", data.get("cars[0].year"));
		assertEquals("apple", data.get("fruits[0]"));
		assertEquals("orange", data.get("fruits[2]"));
	}

	@Test
	public void readsFromInputStream() throws IOException {
		try (InputStream is = getClass().getClassLoader().getResourceAsStream("test.yaml");
				IterableYAMLDataSource source = new IterableYAMLDataSource(is)) {
			source.open();
			assertEquals(17, drain(source));
		}
	}

	@Test
	public void resetRestartsTheIteration() throws IOException {
		try (IterableYAMLDataSource source = new IterableYAMLDataSource(Utils.getFileName("test.yaml"))) {
			source.open();
			int first = drain(source);
			source.reset();
			int second = drain(source);
			assertEquals(first, second);
		}
	}

	@Test
	public void openTwiceThrows() throws IOException {
		try (IterableYAMLDataSource source = new IterableYAMLDataSource(Utils.getFileName("test.yaml"))) {
			source.open();
			assertThrows(IllegalStateException.class, source::open);
		}
	}

	@Test
	public void hasMoreDataBeforeOpenThrows() throws IOException {
		try (IterableYAMLDataSource source = new IterableYAMLDataSource(Utils.getFileName("test.yaml"))) {
			assertThrows(IllegalStateException.class, source::hasMoreData);
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
