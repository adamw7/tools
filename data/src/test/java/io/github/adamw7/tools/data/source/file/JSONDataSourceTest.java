package io.github.adamw7.tools.data.source.file;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.params.provider.Arguments.of;

import java.io.IOException;
import java.util.stream.Stream;

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
        assertArrayEquals(new String[]{"cars", "fruits", "year", "city", "name", "model", "state", "people", "age", "manufacturer"}, columnNames);
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
}

