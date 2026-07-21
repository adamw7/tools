package io.github.adamw7.tools.data.source.file;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.params.provider.Arguments.of;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.List;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import io.github.adamw7.tools.data.Utils;
import io.github.adamw7.tools.data.source.interfaces.ColumnarDataSource;
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
		String[] firstRow = null;
		while (source.hasMoreData()) {
			String[] row = source.nextRow();
			if (row != null) {
				if (i == 0) {
					firstRow = row;
				}
				++i;
				assertEquals(6, row.length);
			}
		}
		Utils.close(source);
		assertEquals(4, i);
		assertArrayEquals(new String[] { "John", "Doe", "120 jefferson st.", "Riverside", " NJ", " 08075" }, firstRow);
	}

	@ParameterizedTest
	@MethodSource("happyPathWithQuotesAndColumnsArgs")
	public void happyPathQuotesAndColumns(ColumnarDataSource source) {
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
		String[] firstRow = null;
		while (source.hasMoreData()) {
			String[] row = source.nextRow();
			if (row != null) {
				if (i == 0) {
					firstRow = row;
				}
				++i;
				assertEquals(10, row.length);
			}
		}
		Utils.close(source);
		assertEquals(100, i);
		// The quoted second field embeds a comma, so quote-aware splitting must keep
		// it as a single field instead of breaking the row into 11 columns.
		assertEquals("1", firstRow[0]);
		assertEquals("\"Eldon Base for stackable storage shelf, platinum\"", firstRow[1]);
		assertEquals("Muhammed MacIntyre", firstRow[2]);
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
	public void closeReleasesUnderlyingScanner() throws IOException {
		InMemoryCSVDataSource source = Utils.createInMemoryDataSource(Utils.getHouseholdFile(), COLUMNS_ROW);
		source.open();
		source.close();
		// Once the scanner is genuinely closed, any further read attempt fails fast
		// instead of silently keeping the file handle open.
		assertThrows(IllegalStateException.class, source::nextRow);
	}

	@Test
	public void closeReleasesUnderlyingScannerWithoutColumns() throws IOException {
		InMemoryCSVDataSource source = Utils.createInMemoryDataSource(Utils.getFileName("addresses.csv"));
		source.open();
		source.close();
		assertThrows(IllegalStateException.class, source::nextRow);
	}

	@Test
	public void closeIsIdempotent() throws IOException {
		InMemoryCSVDataSource source = Utils.createInMemoryDataSource(Utils.getHouseholdFile(), COLUMNS_ROW);
		source.open();
		source.close();
		// A second close must be a harmless no-op now that the source is flagged closed.
		assertDoesNotThrow(source::close);
	}

	@Test
	public void closeBeforeOpenDoesNotThrow() throws IOException {
		InMemoryCSVDataSource source = Utils.createInMemoryDataSource(Utils.getHouseholdFile(), COLUMNS_ROW);
		// The source was never opened, so close must not attempt to touch the scanner.
		assertDoesNotThrow(source::close);
	}

	@Test
	public void resetReopensSourceAfterClose() {
		InMemoryCSVDataSource source = Utils.createInMemoryDataSource(Utils.getHouseholdFile(), COLUMNS_ROW);
		int firstPass = source.readAll().size();
		source.reset();
		int secondPass = source.readAll().size();
		Utils.close(source);
		// reset() closes the exhausted scanner and opens a fresh one, so the same
		// rows are produced again instead of an empty (or leaked) read.
		assertEquals(70, firstPass);
		assertEquals(firstPass, secondPass);
		assertEquals(15, source.getColumnNames().length);
		assertEquals("hlpi_name", source.getColumnNames()[0]);
	}

	@Test
	public void inMemoryReadAll() {
		String fileName = Utils.getHouseholdFile();
		InMemoryCSVDataSource inMemoryDataSource = Utils.createInMemoryDataSource(fileName, 1);
		List<String[]> data = inMemoryDataSource.readAll();
		Utils.close(inMemoryDataSource);
		assertEquals(70, data.size());
		assertEquals(15, data.get(0).length);
		assertEquals("All households", data.get(0)[0]);
		assertEquals("2008", data.get(0)[1]);
	}

	@Test
	public void stream() {
		try (InputStream stream = new FileInputStream(Utils.getFileName("addresses.csv"))) {
			IterableDataSource dataSource = new CSVDataSource(stream);
			dataSource.open();
			int i = 0;
			String[] firstRow = null;
			while (dataSource.hasMoreData()) {
				String[] row = dataSource.nextRow();
				if (row != null) {
					if (i == 0) {
						firstRow = row;
					}
					i++;
				}
			}
			assertEquals(4, i);
			assertEquals(6, firstRow.length);
			assertEquals("John", firstRow[0]);
			dataSource.close();
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
		
	}
}
