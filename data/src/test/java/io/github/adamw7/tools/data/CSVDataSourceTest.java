package io.github.adamw7.tools.data;


import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;

import java.util.stream.Stream;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import io.github.adamw7.tools.data.source.InMemoryCSVDataSource;
import io.github.adamw7.tools.data.source.interfaces.IterableDataSource;

public class CSVDataSourceTest {

	static Stream<Arguments> happyPathArgs() {
		IterableDataSource dataSource = Utils.createDataSource(Utils.getFileName("addresses.csv"));
		InMemoryCSVDataSource inMemoryDataSource = Utils.createInMemoryDataSource(Utils.getFileName("addresses.csv"));
		return Stream.of(
				Arguments.of(Utils.named(dataSource)),
				Arguments.of(Utils.named(inMemoryDataSource)));
	}
	
	static Stream<Arguments> happyPathWithColumnsArgs() {
		IterableDataSource dataSource = Utils.createDataSource(Utils.getHouseholdFile(), 1);
		InMemoryCSVDataSource inMemoryDataSource = Utils.createInMemoryDataSource(Utils.getHouseholdFile(), 1);
		return Stream.of(
				Arguments.of(Utils.named(dataSource)),
				Arguments.of(Utils.named(inMemoryDataSource)));
	}
	
	static Stream<Arguments> happyPathWithQuotesNoColumnsArgs() {
		IterableDataSource dataSource = Utils.createDataSource(Utils.getSampleFile());
		InMemoryCSVDataSource inMemoryDataSource = Utils.createInMemoryDataSource(Utils.getSampleFile());
		return Stream.of(
				Arguments.of(Utils.named(dataSource)),
				Arguments.of(Utils.named(inMemoryDataSource)));
	}
	
	static Stream<Arguments> happyPathWithQuotesAndColumnsArgs() {
		IterableDataSource dataSource = Utils.createDataSource(Utils.getIndustryFile(), 1);
		InMemoryCSVDataSource inMemoryDataSource = Utils.createInMemoryDataSource(Utils.getIndustryFile(), 1);
		return Stream.of(
				Arguments.of(Utils.named(dataSource)),
				Arguments.of(Utils.named(inMemoryDataSource)));
	}

	@ParameterizedTest
	@MethodSource("happyPathArgs")
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
	@MethodSource("happyPathWithQuotesAndColumnsArgs")
	public void happyPathQuotesAndColumns(IterableDataSource source) {
		try {
			source.open();
			assertEquals(source.getColumnNames()[0], "SIC Code");
			assertEquals(source.getColumnNames()[1], "Description");
						
			int i = 0;
			while (source.hasMoreData()) {
				String[] row = source.nextRow();
				if (row != null) {
					++i;
					assertEquals(2, row.length);					
				}
			}
			source.close();
			assertEquals(731, i);
		} catch (Exception e) {
			fail(e.getMessage());
		}
	}
	
	@ParameterizedTest
	@MethodSource("happyPathWithQuotesNoColumnsArgs")
	public void happyPathQuotesNoColumns(IterableDataSource source) {
		try {
			source.open();
			
			int i = 0;
			while (source.hasMoreData()) {
				String[] row = source.nextRow();
				if (row != null) {
					++i;
					assertEquals(10, row.length);					
				}
			}
			source.close();
			assertEquals(100, i);
		} catch (Exception e) {
			fail(e.getMessage());
		}
	}

	
	@ParameterizedTest
	@MethodSource("happyPathWithColumnsArgs")
	public void happyPathWithColumns(IterableDataSource source) {
		try {
			source.open();
			
			int i = 0;
			while (source.hasMoreData()) {
				String[] row = source.nextRow();
				if (row != null) {
					++i;
					assertEquals(15, row.length);					
				}
			}
			source.close();
			assertEquals(70, i);
		} catch (Exception e) {
			fail(e.getMessage());
		}
	}
	
}
