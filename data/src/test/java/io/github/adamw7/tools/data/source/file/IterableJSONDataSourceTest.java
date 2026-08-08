package io.github.adamw7.tools.data.source.file;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import io.github.adamw7.tools.data.Utils;

public class IterableJSONDataSourceTest extends AbstractIterableDataSourceTest {

	@Override
	protected IterableJSONDataSource newSource() {
		return new IterableJSONDataSource(Utils.getFileName("test.json"));
	}

	@Test
	public void flattensNestedValues() throws IOException {
		Map<String, String> data = collect(newSource());
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
	public void nextRowsLoadsRequestedBatchThenDrainsRemainder() throws IOException {
		try (IterableJSONDataSource source = newSource()) {
			source.open();

			List<String[]> firstBatch = source.nextRows(5);
			assertEquals(5, firstBatch.size());

			List<String[]> rest = source.nextRows(100);
			assertEquals(12, rest.size());

			assertTrue(source.nextRows(10).isEmpty());
		}
	}
}
