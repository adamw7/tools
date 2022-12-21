package io.github.adamw7.tools.data;

import static org.junit.Assert.assertThat;
import static org.junit.Assert.fail;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;

import org.junit.jupiter.api.Test;

import io.github.adamw7.tools.data.interfaces.DataSource;

public class CSVDataSourceTest {

	@Test
	public void happyPath() {
		try (DataSource source = new CSVDataSource(getFileName("addresses.csv"))) {
			source.open();
			
			int i = 0;
			while (source.hasMoreData()) {
				String[] row = source.nextRow();
				if (row != null) {
					++i;
					System.out.println(Arrays.toString(row));
					assertEquals(6, row.length);					
				}
			}
			assertEquals(4, i);
		} catch (Exception e) {
			fail(e.getMessage());
		}
	}

	private String getFileName(String fileName) {
		Path resourceDirectory = Paths.get("src", "test", "resources", fileName);
		return resourceDirectory.toFile().getAbsolutePath();
	}
}
