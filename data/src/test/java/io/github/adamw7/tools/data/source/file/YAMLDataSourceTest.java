package io.github.adamw7.tools.data.source.file;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;

import io.github.adamw7.tools.data.Utils;

public class YAMLDataSourceTest {

	@Test
	public void testGetColumnNames() throws IOException {
		try (InMemoryYAMLDataSource source = new InMemoryYAMLDataSource(Utils.getFileName("test.yaml"))) {
			source.open();
			String[] columnNames = source.getColumnNames();
			Set<String> names = new HashSet<>(Arrays.asList(columnNames));
			assertTrue(names.contains("people[0].name"));
			assertTrue(names.contains("people[0].age"));
			assertTrue(names.contains("people[0].address.city"));
			assertTrue(names.contains("cars[0].manufacturer"));
			assertTrue(names.contains("fruits[0]"));
		}
	}

	@Test
	public void testFlattenedData() throws IOException {
		try (InMemoryYAMLDataSource source = new InMemoryYAMLDataSource(Utils.getFileName("test.yaml"))) {
			source.open();
			int rowCount = 0;
			while (source.hasMoreData()) {
				String[] row = source.nextRow();
				rowCount++;
				assertEquals(2, row.length);
				assertNotNull(row[0]);
				assertNotNull(row[1]);
			}
			assertEquals(17, rowCount);
		}
	}

	@Test
	public void testSpecificValues() throws IOException {
		try (InMemoryYAMLDataSource source = new InMemoryYAMLDataSource(Utils.getFileName("test.yaml"))) {
			List<String[]> data = source.readAll();
			var map = new HashMap<String, String>();
			for (String[] row : data) {
				map.put(row[0], row[1]);
			}
			assertEquals("Alice", map.get("people[0].name"));
			assertEquals("30", map.get("people[0].age"));
			assertEquals("New York", map.get("people[0].address.city"));
			assertEquals("NY", map.get("people[0].address.state"));
			assertEquals("Bob", map.get("people[1].name"));
			assertEquals("25", map.get("people[1].age"));
			assertEquals("Toyota", map.get("cars[0].manufacturer"));
			assertEquals("Camry", map.get("cars[0].model"));
			assertEquals("2020", map.get("cars[0].year"));
			assertEquals("Honda", map.get("cars[1].manufacturer"));
			assertEquals("apple", map.get("fruits[0]"));
			assertEquals("banana", map.get("fruits[1]"));
			assertEquals("orange", map.get("fruits[2]"));
		}
	}

	@Test
	public void testInputStream() throws IOException {
		try (InputStream is = getClass().getClassLoader().getResourceAsStream("test.yaml");
			 InMemoryYAMLDataSource source = new InMemoryYAMLDataSource(is)) {
			source.open();
			String[] columnNames = source.getColumnNames();
			assertEquals(17, columnNames.length);
		}
	}

	@Test
	public void testReset() throws IOException {
		try (InMemoryYAMLDataSource source = new InMemoryYAMLDataSource(Utils.getFileName("test.yaml"))) {
			source.open();
			int firstCount = 0;
			while (source.hasMoreData()) {
				source.nextRow();
				firstCount++;
			}
			source.reset();
			int secondCount = 0;
			while (source.hasMoreData()) {
				source.nextRow();
				secondCount++;
			}
			assertEquals(firstCount, secondCount);
		}
	}

	@Test
	public void testOpenTwiceThrows() throws IOException {
		try (InMemoryYAMLDataSource source = new InMemoryYAMLDataSource(Utils.getFileName("test.yaml"))) {
			source.open();
			assertThrows(IllegalStateException.class, source::open);
		}
	}

	@Test
	public void testNotOpenThrows() throws IOException {
		try (InMemoryYAMLDataSource source = new InMemoryYAMLDataSource(Utils.getFileName("test.yaml"))) {
			assertThrows(IllegalStateException.class, source::hasMoreData);
		}
	}
}
