package io.github.adamw7.tools.data.source.file;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

import org.junit.jupiter.api.Test;

import io.github.adamw7.tools.data.Utils;

public class TOONDataSourceTest {

	@Test
	public void testGetColumnNames() throws IOException {
		try (InMemoryTOONDataSource source = new InMemoryTOONDataSource(Utils.getFileName("test.toon"))) {
			source.open();
			String[] columnNames = source.getColumnNames();
			assertNotNull(columnNames);
			assertEquals(27, columnNames.length);
		}
	}

	@Test
	public void testIterateData() throws IOException {
		try (InMemoryTOONDataSource source = new InMemoryTOONDataSource(Utils.getFileName("test.toon"))) {
			source.open();
			int rowCount = 0;
			while (source.hasMoreData()) {
				String[] row = source.nextRow();
				if (row != null) {
					rowCount++;
					assertEquals(2, row.length);
					assertNotNull(row[0]);
					assertNotNull(row[1]);
				}
			}
			assertEquals(27, rowCount);
		}
	}

	@Test
	public void testReadAll() throws IOException {
		try (InMemoryTOONDataSource source = new InMemoryTOONDataSource(Utils.getFileName("test.toon"))) {
			List<String[]> data = source.readAll();
			assertNotNull(data);
			assertFalse(data.isEmpty());
			for (String[] row : data) {
				assertEquals(2, row.length);
				assertNotNull(row[0]);
				assertNotNull(row[1]);
			}
		}
	}

	@Test
	public void testReset() throws IOException {
		try (InMemoryTOONDataSource source = new InMemoryTOONDataSource(Utils.getFileName("test.toon"))) {
			source.open();

			// Read all data
			while (source.hasMoreData()) {
				source.nextRow();
			}

			// Reset and verify we can read again
			source.reset();
			assertTrue(source.hasMoreData());
			String[] row = source.nextRow();
			assertNotNull(row);
		}
	}

	@Test
	public void testOpenTwiceThrowsException() throws IOException {
		try (InMemoryTOONDataSource source = new InMemoryTOONDataSource(Utils.getFileName("test.toon"))) {
			source.open();
			assertThrows(IllegalStateException.class, () -> source.open());
		}
	}

	@Test
	public void testNextRowBeforeOpenThrowsException() throws IOException {
		try (InMemoryTOONDataSource source = new InMemoryTOONDataSource(Utils.getFileName("test.toon"))) {
			assertThrows(IllegalStateException.class, () -> source.nextRow());
		}
	}

	@Test
	public void testHasMoreDataBeforeOpenThrowsException() throws IOException {
		try (InMemoryTOONDataSource source = new InMemoryTOONDataSource(Utils.getFileName("test.toon"))) {
			assertThrows(IllegalStateException.class, () -> source.hasMoreData());
		}
	}

	@Test
	public void testPrimitiveArray() throws IOException {
		String toon = "tags[3]: admin,ops,dev";
		try (InMemoryTOONDataSource source = new InMemoryTOONDataSource(
				new ByteArrayInputStream(toon.getBytes(StandardCharsets.UTF_8)))) {
			source.open();

			String[] columnNames = source.getColumnNames();
			assertTrue(columnNames.length > 0);

			// Should contain the array elements
			boolean foundAdmin = false;
			boolean foundOps = false;
			boolean foundDev = false;
			while (source.hasMoreData()) {
				String[] row = source.nextRow();
				if (row != null && row[1] != null) {
					if (row[1].equals("admin")) foundAdmin = true;
					if (row[1].equals("ops")) foundOps = true;
					if (row[1].equals("dev")) foundDev = true;
				}
			}
			assertTrue(foundAdmin);
			assertTrue(foundOps);
			assertTrue(foundDev);
		}
	}

	@Test
	public void testTabularArray() throws IOException {
		String toon = "users[2]{id,name,role}:\n  1,Alice,admin\n  2,Bob,user";
		try (InMemoryTOONDataSource source = new InMemoryTOONDataSource(
				new ByteArrayInputStream(toon.getBytes(StandardCharsets.UTF_8)))) {
			source.open();

			boolean foundAlice = false;
			boolean foundBob = false;
			while (source.hasMoreData()) {
				String[] row = source.nextRow();
				if (row != null && row[1] != null) {
					if (row[1].equals("Alice")) foundAlice = true;
					if (row[1].equals("Bob")) foundBob = true;
				}
			}
			assertTrue(foundAlice);
			assertTrue(foundBob);
		}
	}

	@Test
	public void testNestedObject() throws IOException {
		String toon = "context:\n  task: Our favorite hikes\n  location: Boulder";
		try (InMemoryTOONDataSource source = new InMemoryTOONDataSource(
				new ByteArrayInputStream(toon.getBytes(StandardCharsets.UTF_8)))) {
			source.open();

			boolean foundTask = false;
			boolean foundLocation = false;
			while (source.hasMoreData()) {
				String[] row = source.nextRow();
				if (row != null) {
					if (row[0].equals("context.task") && row[1].equals("Our favorite hikes")) foundTask = true;
					if (row[0].equals("context.location") && row[1].equals("Boulder")) foundLocation = true;
				}
			}
			assertTrue(foundTask);
			assertTrue(foundLocation);
		}
	}

	@Test
	public void testQuotedStrings() throws IOException {
		String toon = "message: \"Hello, World!\"";
		try (InMemoryTOONDataSource source = new InMemoryTOONDataSource(
				new ByteArrayInputStream(toon.getBytes(StandardCharsets.UTF_8)))) {
			source.open();

			boolean found = false;
			while (source.hasMoreData()) {
				String[] row = source.nextRow();
				if (row != null && row[0].equals("message") && row[1].equals("Hello, World!")) {
					found = true;
				}
			}
			assertTrue(found);
		}
	}

	@Test
	public void testIterator() throws IOException {
		try (InMemoryTOONDataSource source = new InMemoryTOONDataSource(Utils.getFileName("test.toon"))) {
			source.open();

			var iterator = source.iterator();
			assertNotNull(iterator);
			assertTrue(iterator.hasNext());

			int count = 0;
			while (iterator.hasNext()) {
				String[] row = iterator.next();
				assertNotNull(row);
				count++;
			}
			assertEquals(27, count);
		}
	}
}
