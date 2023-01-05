package io.github.adamw7.tools.data;

import java.nio.file.Path;
import java.nio.file.Paths;

import org.junit.jupiter.api.Named;

import io.github.adamw7.tools.data.source.CSVDataSource;
import io.github.adamw7.tools.data.source.InMemoryCSVDataSource;
import io.github.adamw7.tools.data.source.interfaces.IterableDataSource;

public class Utils {

	public static String getFileName(String fileName) {
		Path resourceDirectory = Paths.get("src", "test", "resources", fileName);
		return resourceDirectory.toFile().getAbsolutePath();
	}

	static String getHouseholdFile() {
		return getFileName("Household-living-costs-price-indexes-September-2022-quarter-group-facts.csv");
	}

	public static String getSampleFile() {
		return getFileName("sample.csv");
	}
	
	public static String getIndustryFile() {
		return getFileName("industry_sic.csv");
	}

	static IterableDataSource createDataSource(String file, int columnsRow) {
		try {
			return new CSVDataSource(file, columnsRow);
		} catch (Exception e) {
			return null;
		}
	}

	static IterableDataSource createDataSource(String file) {
		try {
			return new CSVDataSource(file);
		} catch (Exception e) {
			return null;
		}
	}

	static InMemoryCSVDataSource createInMemoryDataSource(String file, int columnsRow) {
		try {
			return new InMemoryCSVDataSource(file, columnsRow);
		} catch (Exception e) {
			return null;
		}
	}

	static InMemoryCSVDataSource createInMemoryDataSource(String file) {
		try {
			return new InMemoryCSVDataSource(file);
		} catch (Exception e) {
			return null;
		}
	}

	static Named<Object> named(Object dataSource) {
		return Named.of(dataSource.getClass().getSimpleName(), dataSource);
	}

}
