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
import io.github.adamw7.tools.data.source.interfaces.ColumnarDataSource;

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

	static Stream<Arguments> raggedSources() {
		String raggedFile = Utils.getFileName("ragged.csv");

		return Stream.of(of(new NoMemoryUniquenessCheck(Utils.createDataSource(raggedFile, COLUMNS_ROW))),
				of(new InMemoryUniquenessCheck(Utils.createInMemoryDataSource(raggedFile, COLUMNS_ROW))));
	}

	/**
	 * A CSV line with a delimiter missing is split into an array shorter than the header,
	 * and projecting the key out of it used to fail with a bare
	 * "ArrayIndexOutOfBoundsException: Index 2 out of bounds for length 2", which named
	 * neither the row nor the column. A ragged row is expected input for a library reading
	 * somebody else's file, so it is reported as a fault in the data, positioned.
	 */
	@ParameterizedTest
	@MethodSource("raggedSources")
	void raggedRowNamesItsPositionAndArity(Uniqueness uniqueness) {
		RaggedRowException thrown = assertThrows(RaggedRowException.class, () -> uniqueness.exec("city"),
				"Expected exec method to throw, but it didn't");

		assertEquals("Data row 2 is ragged: the key reads at least 3 columns but the row has 2: [2, Bob]",
				thrown.getMessage());
	}

	/** A key read entirely out of the columns every row does have is unaffected by the short one. */
	@ParameterizedTest
	@MethodSource("raggedSources")
	void raggedRowIsReadWhereverTheKeyFitsIt(Uniqueness uniqueness) {
		assertTrue(uniqueness.exec("id").isUnique());
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

	/**
	 * A column is matched by name case-insensitively, so ("year1", "YEAR1") names one
	 * column twice. Folded case-sensitively the pair passed this check and then
	 * collapsed to a single index, so the run checked a one-column key the caller never
	 * asked for — and a narrower key can only report duplicates the named one has not.
	 */
	@ParameterizedTest
	@MethodSource("happyPath")
	void negativeDuplicatesDifferingOnlyInCase(Uniqueness uniqueness) throws Exception {
		IllegalArgumentException thrown = assertThrows(IllegalArgumentException.class,
				() -> uniqueness.exec("year1", "YEAR1"), "Expected exec method to throw, but it didn't");

		assertEquals("Duplicate in input: YEAR1", thrown.getMessage());
	}

	/**
	 * The counterpart of the rejection above: two genuinely different columns named in
	 * a case the source does not use are still the key the caller asked for.
	 */
	@ParameterizedTest
	@MethodSource("happyPath")
	void uniqueKeyNamedInADifferentCase(Uniqueness uniqueness) throws Exception {
		assertTrue(uniqueness.exec("YEAR1", "HLPI_NAME").isUnique());
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

	private static final class ClosingSpyDataSource implements ColumnarDataSource {

		private final ColumnarDataSource delegate;
		private boolean closed = false;

		ClosingSpyDataSource(ColumnarDataSource delegate) {
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

	@Test
	public void allColumnsOnAHeaderlessSourceNamesTheCause() {
		NoMemoryUniquenessCheck uniqueness = new NoMemoryUniquenessCheck(
				Utils.createDataSource(Utils.getHouseholdFile()));

		// The source was built without a columnsRow, so it has no columns to check.
		// The failure used to reach the caller as "Wrong input: null", which named
		// neither the source nor the header it was created without.
		IllegalStateException thrown = assertThrows(IllegalStateException.class, uniqueness::execForAllColumns);

		assertEquals("Source was created without a header row, so it has no column names; "
				+ "pass the 1-based row the header sits on as columnsRow", thrown.getMessage());
	}

}
