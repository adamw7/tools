package io.github.adamw7.tools.data;


import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;

import java.util.stream.Stream;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;

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
