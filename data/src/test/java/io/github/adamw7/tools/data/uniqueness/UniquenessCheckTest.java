package io.github.adamw7.tools.data.uniqueness;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.params.provider.Arguments.of;

import java.util.HashSet;
import java.util.Set;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import io.github.adamw7.tools.data.DBTest;
import io.github.adamw7.tools.data.Utils;
import io.github.adamw7.tools.data.source.interfaces.IterableDataSource;

public class UniquenessCheckTest extends DBTest {

	private static final int COLUMNS_ROW = 1;
	private static final String[] NOT_UNIQUE_COLUMNS = new String[] { "year1" };
	private static final String[] UNIQUE_COLUMNS = new String[] { "year1", "hlpi_name" };

	static Stream<Arguments> happyPath() {
		String householdFile = Utils.getHouseholdFile();

		return Stream.of(of(new NoMemoryUniquenessCheck(Utils.createDataSource(householdFile, COLUMNS_ROW))),
				of(new InMemoryUniquenessCheck(Utils.createInMemoryDataSource(householdFile, COLUMNS_ROW))),
				of(new NoMemoryUniquenessCheck(Utils.createIterableSQLDataSource(connection, query))),
				of(new InMemoryUniquenessCheck(Utils.createInMemorySQLDataSource(connection, query))));
	}

	@ParameterizedTest
	@MethodSource("happyPath")
	public void happyPathNotUnique(Uniqueness uniqueness) throws Exception {
		Result result = uniqueness.exec(NOT_UNIQUE_COLUMNS);
		assertFalse(result.isUnique());
	}

	@ParameterizedTest
	@MethodSource("happyPath")
	public void happyPathUnique(Uniqueness uniqueness) throws Exception {
		Result result = uniqueness.exec(UNIQUE_COLUMNS);
		assertTrue(result.isUnique());
	}

	@ParameterizedTest
	@MethodSource("happyPath")
	public void happyPathUniqueShouldFindBetterOptions(Uniqueness uniqueness) throws Exception {
		Result result = uniqueness.exec("year1", "hlpi_name", "income");
		assertTrue(result.isUnique());
		Set<Result> betterOptions = result.getBetterOptions();
		assertEquals(3, betterOptions.size());
		assertTrue(betterOptions.contains(new Result(true, new String[] { "year1", "hlpi_name" })));
		Set<Result> incomeOnly = new HashSet<>();
		incomeOnly.add(new Result(true, new String[] { "income" }));
		assertTrue(betterOptions.contains(new Result(true, new String[] { "income", "year1" }, null, incomeOnly)));
		assertTrue(betterOptions.contains(new Result(true, new String[] { "income", "hlpi_name" }, null, incomeOnly)));
	}

	@ParameterizedTest
	@MethodSource("happyPath")
	public void happyPathUniqueShouldNotFindBetterOptions(Uniqueness uniqueness) throws Exception {
		Result result = uniqueness.exec("income");
		assertTrue(result.isUnique());
		assertEquals(0, result.getBetterOptions().size());
	}

	@ParameterizedTest
	@MethodSource("happyPath")
	void negativeWrongColumn(Uniqueness uniqueness) throws Exception {
		String columnName = "notExistingColumn";
		ColumnNotFoundException thrown = assertThrows(ColumnNotFoundException.class, () -> uniqueness.exec(columnName), "Expected exec method to throw, but it didn't");
		String expectedMessage = columnName
				+ " cannot be found in [hlpi_name, year1, hlpi, tot_hhs, own, own_wm, own_prop, own_wm_prop, prop_hhs, age, size, income, expenditure, eqv_income, eqv_exp]";
		assertEquals(expectedMessage.toLowerCase(), thrown.getMessage().toLowerCase());
	}

	@ParameterizedTest
	@MethodSource("happyPath")
	void negativeEmptyInputArray(Uniqueness uniqueness) throws Exception {
		IllegalArgumentException thrown = assertThrows(IllegalArgumentException.class, () -> uniqueness.exec(new String[] {}), "Expected exec method to throw, but it didn't");

		assertEquals("Wrong input: []", thrown.getMessage());
	}

	@ParameterizedTest
	@MethodSource("happyPath")
	void negativeNullInputArray(Uniqueness uniqueness) throws Exception {
		IllegalArgumentException thrown = assertThrows(IllegalArgumentException.class, () -> uniqueness.exec((String[]) null), "Expected exec method to throw, but it didn't");

		assertEquals("Wrong input: null", thrown.getMessage());
	}

	@ParameterizedTest
	@MethodSource("happyPath")
	void negativeNullsInInputArray(Uniqueness uniqueness) throws Exception {
		IllegalArgumentException thrown = assertThrows(IllegalArgumentException.class, () -> uniqueness.exec("hlpi_name", null, "year1"), "Expected exec method to throw, but it didn't");

		assertEquals("Input columns cannot be null", thrown.getMessage());
	}

	@ParameterizedTest
	@MethodSource("happyPath")
	void negativeDuplicatesInInputArray(Uniqueness uniqueness) throws Exception {
		IllegalArgumentException thrown = assertThrows(IllegalArgumentException.class, () -> uniqueness.exec("year1", "year1"), "Expected exec method to throw, but it didn't");

		assertEquals("Duplicate in input: year1", thrown.getMessage());
	}

	@Test
	public void notUniquePathClosesDataSource() throws Exception {
		ClosingSpyDataSource source = new ClosingSpyDataSource(
				Utils.createDataSource(Utils.getHouseholdFile(), COLUMNS_ROW));
		NoMemoryUniquenessCheck uniqueness = new NoMemoryUniquenessCheck(source);

		Result result = uniqueness.exec(NOT_UNIQUE_COLUMNS);

		assertFalse(result.isUnique());
		assertTrue(source.isClosed(), "Data source must be closed when a duplicate is found");
	}

	private static final class ClosingSpyDataSource implements IterableDataSource {

		private final IterableDataSource delegate;
		private boolean closed = false;

		ClosingSpyDataSource(IterableDataSource delegate) {
			this.delegate = delegate;
		}

		boolean isClosed() {
			return closed;
		}

		@Override
		public void close() throws java.io.IOException {
			closed = true;
			delegate.close();
		}

		@Override
		public String[] getColumnNames() {
			return delegate.getColumnNames();
		}

		@Override
		public void open() {
			delegate.open();
		}

		@Override
		public String[] nextRow() {
			return delegate.nextRow();
		}

		@Override
		public boolean hasMoreData() {
			return delegate.hasMoreData();
		}

		@Override
		public void reset() {
			delegate.reset();
		}
	}

	@Test
	public void happyPathAllColumns() {
		InMemoryUniquenessCheck uniqueness = new InMemoryUniquenessCheck(
				Utils.createInMemorySQLDataSource(connection, "SELECT * FROM PEOPLE"));

		Result result = uniqueness.execForAllColumns();
		assertTrue(result.isUnique());
		assertEquals(3, result.getBetterOptions().size());
		Result id = new Result(true, new String[] { "ID" });
		Result name = new Result(true, new String[] { "NAME" });
		
		Set<Result> betterOptions = new HashSet<>();
		betterOptions.add(id);
		betterOptions.add(name);

		assertTrue(result.getBetterOptions().contains(new Result(true, new String[] {"ID", "NAME"}, null, betterOptions)));
		
	}

}
