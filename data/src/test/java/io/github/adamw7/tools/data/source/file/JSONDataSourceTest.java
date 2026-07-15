package io.github.adamw7.tools.data.source.file;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.params.provider.Arguments.of;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import io.github.adamw7.tools.data.Utils;
import io.github.adamw7.tools.data.source.interfaces.IterableDataSource;

public class JSONDataSourceTest {

	static Stream<Arguments> dataSources() {
		IterableDataSource zippedDataSource = new InMemoryJSONDataSource(Utils.getFileName("test.json.gz"));
		IterableDataSource unzippedDataSource = new InMemoryJSONDataSource(Utils.getFileName("test.json"));
		return Stream.of(of(Utils.named(zippedDataSource)), of(Utils.named(unzippedDataSource)));
	}

	@ParameterizedTest
	@MethodSource("dataSources")
    public void testGetColumnNames(InMemoryJSONDataSource source) throws IOException {
		source.open();
        String[] columnNames = source.getColumnNames();
        assertEquals(17, columnNames.length);
        assertEquals(expectedColumnNames(), Set.of(columnNames));
        source.close();
    }

	@ParameterizedTest
	@MethodSource("dataSources")
    public void testFlattenedData(InMemoryJSONDataSource source) throws IOException {
		source.open();
        int rowCount = 0;
        while (source.iterator().hasNext()) {
            String[] row = source.nextRow();
        	rowCount++;

        	assertEquals(2, row.length);
            assertNotNull(row[0]);
            assertNotNull(row[1]);
        }
        assertEquals(17, rowCount);
        source.close();
    }

	@Test
	public void testSpecificValues() throws IOException {
		InMemoryJSONDataSource source = new InMemoryJSONDataSource(Utils.getFileName("test.json"));
		List<String[]> data = source.readAll();
		HashMap<String, String> map = new HashMap<>();
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

	@Test
	public void readsFromInputStream() throws IOException {
		try (InputStream stream = new FileInputStream(Utils.getFileName("test.json"))) {
			InMemoryJSONDataSource source = new InMemoryJSONDataSource(stream);
			source.open();
			Set<String> names = new HashSet<>(Arrays.asList(source.getColumnNames()));
			assertEquals(17, names.size());
			assertTrue(names.contains("people[0].address.city"));
			assertTrue(names.contains("fruits[2]"));
			int rowCount = 0;
			while (source.hasMoreData()) {
				assertNotNull(source.nextRow());
				rowCount++;
			}
			assertEquals(17, rowCount);
			source.close();
		}
	}

	private static Set<String> expectedColumnNames() {
		return Set.of(
				"people[0].name", "people[0].age", "people[0].address.city", "people[0].address.state",
				"people[1].name", "people[1].age", "people[1].address.city", "people[1].address.state",
				"cars[0].manufacturer", "cars[0].model", "cars[0].year",
				"cars[1].manufacturer", "cars[1].model", "cars[1].year",
				"fruits[0]", "fruits[1]", "fruits[2]");
	}
}
