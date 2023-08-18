package io.github.adamw7.tools.data.source.file;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.io.IOException;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.github.adamw7.tools.data.Utils;

public class JSONDataSourceTest {
    private InMemoryJSONDataSource dataSource;

    @BeforeEach
    public void setUp() {
        dataSource = new InMemoryJSONDataSource(Utils.getFileName("test.json"));
        dataSource.open();
    }

    @AfterEach
    public void tearDown() throws IOException {
        dataSource.close();
    }

    @Test
    public void testGetColumnNames() {
        String[] columnNames = dataSource.getColumnNames();
        assertEquals(5, columnNames.length);
        assertArrayEquals(new String[]{"address", "city", "name", "state", "age"}, columnNames);
    }

    @Test
    public void testFlattenedData() {
        int rowCount = 0;
        while (dataSource.iterator().hasNext()) {
            String[] row = dataSource.nextRow();
        	rowCount++;

        	assertEquals(2, row.length);
            assertNotNull(row[0]);
            assertNotNull(row[1]);
        }
        assertEquals(5, rowCount);
    }
}

