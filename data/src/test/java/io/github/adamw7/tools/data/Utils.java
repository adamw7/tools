package io.github.adamw7.tools.data;

import java.io.Closeable;
import java.io.FileNotFoundException;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.Connection;

import org.junit.jupiter.api.Named;

import io.github.adamw7.tools.data.source.db.InMemorySQLDataSource;
import io.github.adamw7.tools.data.source.db.IterableSQLDataSource;
import io.github.adamw7.tools.data.source.file.CSVDataSource;
import io.github.adamw7.tools.data.source.file.InMemoryCSVDataSource;
import io.github.adamw7.tools.data.source.interfaces.IterableDataSource;

public class Utils {

	public static String getFileName(String fileName) {
		Path resourceDirectory = Paths.get("src", "test", "resources", fileName);
		return resourceDirectory.toFile().getAbsolutePath();
	}

	public static String getHouseholdFile() {
		return getFileName("Household.csv");
	}

	public static String getSampleFile() {
		return getFileName("sample.csv");
	}
	
	public static String getIndustryFile() {
		return getFileName("industry_sic.csv");
	}
	
	public static String getIndustryFileZipped() {
		return getFileName("industry_sic.csv.gz");
	}

	public static IterableDataSource createDataSource(String file, int columnsRow) {
		try {
			return new CSVDataSource(file, columnsRow);
		} catch (FileNotFoundException e) {
			throw new UncheckedIOException(e);
		}
	}

	public static IterableDataSource createDataSource(String file) {
		try {
			return new CSVDataSource(file);
		} catch (FileNotFoundException e) {
			throw new UncheckedIOException(e);
		}
	}

	public static InMemoryCSVDataSource createInMemoryDataSource(String file, int columnsRow) {
		try {
			return new InMemoryCSVDataSource(file, columnsRow);
		} catch (FileNotFoundException e) {
			throw new UncheckedIOException(e);
		}
	}

	public static InMemoryCSVDataSource createInMemoryDataSource(String file) {
		try {
			return new InMemoryCSVDataSource(file);
		} catch (FileNotFoundException e) {
			throw new UncheckedIOException(e);
		}
	}

	public static Named<Object> named(Object object) {
		return Named.of(object.getClass().getSimpleName(), object);
	}

	public static IterableSQLDataSource createIterableSQLDataSource(Connection connection, String query) {
		return new IterableSQLDataSource(connection, query);				
	}
	
	public static IterableSQLDataSource createInMemorySQLDataSource(Connection connection, String query) {
		return new InMemorySQLDataSource(connection, query);				
	}

	public static void close(Closeable source) {
		try {
			source.close();
		} catch (Exception ignored) {
		}
	}
}
