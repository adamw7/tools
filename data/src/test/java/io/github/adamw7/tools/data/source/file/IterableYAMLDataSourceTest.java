package io.github.adamw7.tools.data.source.file;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.IOException;
import java.io.InputStream;
import java.util.Map;

import org.junit.jupiter.api.Test;

import io.github.adamw7.tools.data.Utils;

public class IterableYAMLDataSourceTest extends AbstractIterableDataSourceTest {

	@Override
	protected IterableYAMLDataSource newSource() {
		return new IterableYAMLDataSource(Utils.getFileName("test.yaml"));
	}

	@Test
	public void streamsSameRowsAsInMemorySource() throws IOException {
		Map<String, String> inMemory = collect(new InMemoryYAMLDataSource(Utils.getFileName("test.yaml")));
		assertEquals(inMemory, collect(newSource()));
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
}
