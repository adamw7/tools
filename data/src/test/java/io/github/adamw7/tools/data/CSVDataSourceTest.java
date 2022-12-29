package io.github.adamw7.tools.data;


import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;

import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;

import io.github.adamw7.tools.data.source.CSVDataSource;
import io.github.adamw7.tools.data.source.interfaces.IterableDataSource;

public class CSVDataSourceTest {

	static Stream<Arguments> happyPathArgs() {
		return Stream.of(
				Arguments.of(Utils.createDataSource(Utils.getFileName("addresses.csv"))),
				Arguments.of(Utils.createInMemoryDataSource(Utils.getFileName("addresses.csv"))));
	}
	
	static Stream<Arguments> happyPathWithColumnsArgs() {
		return Stream.of(
				Arguments.of(Utils.createDataSource(Utils.getHouseholdFile(), 1)),
				Arguments.of(Utils.createInMemoryDataSource(Utils.getHouseholdFile(), 1)));
	}
	
	@ParameterizedTest
	@VariableSource("happyPathArgs")
	public void happyPathNoColumns(IterableDataSource source) {
		try {
			source.open();
			
			int i = 0;
			while (source.hasMoreData()) {
				String[] row = source.nextRow();
				if (row != null) {
					++i;
					assertEquals(6, row.length);					
				}
			}
			source.close();
			assertEquals(4, i);
		} catch (Exception e) {
			fail(e.getMessage());
		}
	}
	
	@ParameterizedTest
	@VariableSource("happyPathWithColumnsArgs")
	public void happyPathWithColumns() {
		try (IterableDataSource source = new CSVDataSource(householdFile(), 1)) {
			source.open();
			
			int i = 0;
			while (source.hasMoreData()) {
				String[] row = source.nextRow();
				if (row != null) {
					++i;
					assertEquals(15, row.length);					
				}
			}
			assertEquals(70, i);
		} catch (Exception e) {
			fail(e.getMessage());
		}
	}

	private String householdFile() {
		return Utils.getFileName("Household-living-costs-price-indexes-September-2022-quarter-group-facts.csv");
	}
	
}
