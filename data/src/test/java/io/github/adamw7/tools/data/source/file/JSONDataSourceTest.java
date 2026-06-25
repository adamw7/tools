package io.github.adamw7.tools.data.source.file;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.params.provider.Arguments.of;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
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
        assertEquals(10, columnNames.length);
        assertEquals(Set.of("cars", "fruits", "year", "city", "name", "model", "state", "people", "age", "manufacturer"),
                Set.of(columnNames));
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
        assertEquals(10, rowCount);
        source.close();
    }

	@Test
	public void readsFromInputStream() throws IOException {
		try (InputStream stream = new FileInputStream(Utils.getFileName("test.json"))) {
			InMemoryJSONDataSource source = new InMemoryJSONDataSource(stream);
			source.open();
			assertEquals(10, source.getColumnNames().length);
			int rowCount = 0;
			while (source.hasMoreData()) {
				assertNotNull(source.nextRow());
				rowCount++;
			}
			assertEquals(10, rowCount);
			source.close();
		}
	}
}

