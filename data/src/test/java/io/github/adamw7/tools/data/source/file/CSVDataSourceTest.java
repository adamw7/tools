package io.github.adamw7.tools.data.source.file;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.params.provider.Arguments.of;

import java.util.List;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import io.github.adamw7.tools.data.Utils;
import io.github.adamw7.tools.data.source.interfaces.IterableDataSource;

public class CSVDataSourceTest {

	private static final int COLUMNS_ROW = 1;
	
	static Stream<Arguments> happyPathArgs() {
		String fileName = Utils.getFileName("addresses.csv");
		String fileNameZipped = Utils.getFileName("addresses.csv.gz");
		IterableDataSource dataSource = Utils.createDataSource(fileName);
		InMemoryCSVDataSource inMemoryDataSource = Utils.createInMemoryDataSource(fileName);
		IterableDataSource dataSourceZipped = Utils.createDataSource(fileNameZipped);
		InMemoryCSVDataSource inMemoryDataSourceZipped = Utils.createInMemoryDataSource(fileNameZipped);

		return Stream.of(of(Utils.named(dataSource)), of(Utils.named(dataSourceZipped)),
				of(Utils.named(inMemoryDataSource)), of(Utils.named(inMemoryDataSourceZipped)));
	}

	static Stream<Arguments> happyPathWithColumnsArgs() {
		IterableDataSource dataSource = Utils.createDataSource(Utils.getHouseholdFile(), COLUMNS_ROW);
		InMemoryCSVDataSource inMemoryDataSource = Utils.createInMemoryDataSource(Utils.getHouseholdFile(), COLUMNS_ROW);
		return Stream.of(of(Utils.named(dataSource)), of(Utils.named(inMemoryDataSource)));
	}

	static Stream<Arguments> happyPathWithQuotesNoColumnsArgs() {
		IterableDataSource dataSource = Utils.createDataSource(Utils.getSampleFile());
		InMemoryCSVDataSource inMemoryDataSource = Utils.createInMemoryDataSource(Utils.getSampleFile());
		return Stream.of(of(Utils.named(dataSource)), of(Utils.named(inMemoryDataSource)));
	}

	static Stream<Arguments> happyPathWithQuotesAndColumnsArgs() {
		IterableDataSource dataSource = Utils.createDataSource(Utils.getIndustryFile(), COLUMNS_ROW);
		InMemoryCSVDataSource inMemoryDataSource = Utils.createInMemoryDataSource(Utils.getIndustryFile(), COLUMNS_ROW);
		IterableDataSource dataSourceZipped = Utils.createDataSource(Utils.getIndustryFileZipped(), COLUMNS_ROW);
		InMemoryCSVDataSource inMemoryDataSourceZipped = Utils.createInMemoryDataSource(Utils.getIndustryFileZipped(),
				COLUMNS_ROW);

		return Stream.of(of(Utils.named(dataSource)), of(Utils.named(dataSourceZipped)),
				of(Utils.named(inMemoryDataSource)), of(Utils.named(inMemoryDataSourceZipped)));
	}

	@ParameterizedTest
	@MethodSource("happyPathArgs")
	public void happyPathNoColumns(IterableDataSource source) {
		source.open();

		int i = 0;
		while (source.hasMoreData()) {
			String[] row = source.nextRow();
			if (row != null) {
				++i;
				assertEquals(6, row.length);
			}
		}
		Utils.close(source);
		assertEquals(4, i);
	}

	@ParameterizedTest
	@MethodSource("happyPathWithQuotesAndColumnsArgs")
	public void happyPathQuotesAndColumns(IterableDataSource source) {
		source.open();
		assertEquals("SIC Code", source.getColumnNames()[0]);
		assertEquals("Description",source.getColumnNames()[1]);

		int i = 0;
		while (source.hasMoreData()) {
			String[] row = source.nextRow();
			if (row != null) {
				++i;
				assertEquals(2, row.length);
			}
		}
		Utils.close(source);
		assertEquals(731, i);
	}

	@ParameterizedTest
	@MethodSource("happyPathWithQuotesNoColumnsArgs")
	public void happyPathQuotesNoColumns(IterableDataSource source) {
		source.open();

		int i = 0;
		while (source.hasMoreData()) {
			String[] row = source.nextRow();
			if (row != null) {
				++i;
				assertEquals(10, row.length);
			}
		}
		Utils.close(source);
		assertEquals(100, i);
	}

	@ParameterizedTest
	@MethodSource("happyPathWithColumnsArgs")
	public void happyPathWithColumns(IterableDataSource source) {
		source.open();

		int i = 0;
		while (source.hasMoreData()) {
			String[] row = source.nextRow();
			if (row != null) {
				++i;
				assertEquals(15, row.length);
			}
		}
		Utils.close(source);
		assertEquals(70, i);
	}

	@Test
	public void inMemoryReadAll() {
		String fileName = Utils.getHouseholdFile();
		InMemoryCSVDataSource inMemoryDataSource = Utils.createInMemoryDataSource(fileName, 1);
		List<String[]> data = inMemoryDataSource.readAll();
		Utils.close(inMemoryDataSource);
		assertEquals(70, data.size());
	}

}
