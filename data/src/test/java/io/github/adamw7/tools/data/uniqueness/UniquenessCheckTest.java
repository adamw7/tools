package io.github.adamw7.tools.data.uniqueness;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.params.provider.Arguments.of;

import java.lang.reflect.InvocationTargetException;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import io.github.adamw7.tools.data.DBTest;
import io.github.adamw7.tools.data.Utils;
import io.github.adamw7.tools.data.source.db.IterableSQLDataSource;
import io.github.adamw7.tools.data.source.interfaces.IterableDataSource;

public class UniquenessCheckTest extends DBTest {

	private static final int COLUMNS_ROW = 1;
	private static final String[] NOT_UNIQUE_COLUMNS = new String[] { "year1" };
	private static final String[] UNIQUE_COLUMNS = new String[] { "year1", "hlpi_name" };

	static Stream<Arguments> happyPath() {
		String householdFile = Utils.getHouseholdFile();

		return Stream.of(of(NoMemoryUniquenessCheck.class, Utils.createDataSource(householdFile, COLUMNS_ROW)),
				of(InMemoryUniquenessCheck.class, Utils.createInMemoryDataSource(householdFile, COLUMNS_ROW)),
				of(NoMemoryUniquenessCheck.class, Utils.createIterableSQLDataSource(connection, query)),
				of(InMemoryUniquenessCheck.class, Utils.createInMemorySQLDataSource(connection, query)));
	}

	@ParameterizedTest
	@MethodSource("happyPath")
	public void happyPathNotUnique(Class<AbstractUniqueness> uniquenessClass, IterableDataSource source)
			throws Exception {
		AbstractUniqueness uniqueness = initUniquenessCheck(uniquenessClass, source);

		Result result = uniqueness.exec(NOT_UNIQUE_COLUMNS);
		assertFalse(result.isUnique());
	}

	private AbstractUniqueness initUniquenessCheck(Class<AbstractUniqueness> uniquenessClass, IterableDataSource source)
			throws InstantiationException, IllegalAccessException, IllegalArgumentException, InvocationTargetException,
			NoSuchMethodException, SecurityException {
		AbstractUniqueness uniqueness = uniquenessClass.getConstructor().newInstance();
		uniqueness.setDataSource(source);
		return uniqueness;
	}

	@ParameterizedTest
	@MethodSource("happyPath")
	public void happyPathUnique(Class<AbstractUniqueness> uniquenessClass, IterableDataSource source) throws Exception {
		AbstractUniqueness uniqueness = initUniquenessCheck(uniquenessClass, source);

		Result result = uniqueness.exec(UNIQUE_COLUMNS);
		assertTrue(result.isUnique());
	}

	@ParameterizedTest
	@MethodSource("happyPath")
	public void happyPathUniqueShouldFindBetterOptions(Class<AbstractUniqueness> uniquenessClass,
			IterableDataSource source) throws Exception {
		AbstractUniqueness uniqueness = initUniquenessCheck(uniquenessClass, source);
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
	public void happyPathUniqueShouldNotFindBetterOptions(Class<AbstractUniqueness> uniquenessClass,
			IterableDataSource source) throws Exception {
		AbstractUniqueness uniqueness = initUniquenessCheck(uniquenessClass, source);
		Result result = uniqueness.exec("income");
		assertTrue(result.isUnique());
		assertEquals(0, result.getBetterOptions().size());
	}

	@ParameterizedTest
	@MethodSource("happyPath")
	void negativeWrongColumn(Class<AbstractUniqueness> uniquenessClass, IterableDataSource source) throws Exception {
		AbstractUniqueness uniqueness = initUniquenessCheck(uniquenessClass, source);
		String columnName = "notExistingColumn";
		ColumnNotFoundException thrown = assertThrows(ColumnNotFoundException.class, () -> uniqueness.exec(columnName), "Expected exec method to throw, but it didn't");
		String expectedMessage = columnName
				+ " cannot be found in [hlpi_name, year1, hlpi, tot_hhs, own, own_wm, own_prop, own_wm_prop, prop_hhs, age, size, income, expenditure, eqv_income, eqv_exp]";
		assertEquals(expectedMessage.toLowerCase(), thrown.getMessage().toLowerCase());
	}

	@ParameterizedTest
	@MethodSource("happyPath")
	void negativeEmptyInputArray(Class<AbstractUniqueness> uniquenessClass, IterableDataSource source)
			throws Exception {
		AbstractUniqueness uniqueness = initUniquenessCheck(uniquenessClass, source);
		IllegalArgumentException thrown = assertThrows(IllegalArgumentException.class, uniqueness::exec, "Expected exec method to throw, but it didn't");

		assertEquals("Wrong input: []", thrown.getMessage());
	}

	@ParameterizedTest
	@MethodSource("happyPath")
	void negativeNullInputArray(Class<AbstractUniqueness> uniquenessClass, IterableDataSource source) throws Exception {
		AbstractUniqueness uniqueness = initUniquenessCheck(uniquenessClass, source);
		IllegalArgumentException thrown = assertThrows(IllegalArgumentException.class, () -> uniqueness.exec((String[]) null), "Expected exec method to throw, but it didn't");

		assertEquals("Wrong input: null", thrown.getMessage());
	}

	@ParameterizedTest
	@MethodSource("happyPath")
	void negativeNullsInInputArray(Class<AbstractUniqueness> uniquenessClass, IterableDataSource source)
			throws Exception {
		AbstractUniqueness uniqueness = initUniquenessCheck(uniquenessClass, source);
		IllegalArgumentException thrown = assertThrows(IllegalArgumentException.class, () -> uniqueness.exec("hlpi_name", null, "year1"), "Expected exec method to throw, but it didn't");

		assertEquals("Input columns cannot be null", thrown.getMessage());
	}

	@ParameterizedTest
	@MethodSource("happyPath")
	void negativeDuplicatesInInputArray(Class<AbstractUniqueness> uniquenessClass, IterableDataSource source)
			throws Exception {
		AbstractUniqueness uniqueness = initUniquenessCheck(uniquenessClass, source);
		IllegalArgumentException thrown = assertThrows(IllegalArgumentException.class, () -> uniqueness.exec("year1", "year1"), "Expected exec method to throw, but it didn't");

		assertEquals("Duplicate in input: year1", thrown.getMessage());
	}

	@Test
	public void negativeInMemorySourceVsNoMemoryCheck() {
		AbstractUniqueness uniqueness = new InMemoryUniquenessCheck();
		IllegalArgumentException thrown = assertThrows(IllegalArgumentException.class, () -> uniqueness.setDataSource(new IterableSQLDataSource(connection, query)), "Expected setDataSource method to throw, but it didn't");

		assertEquals("Expected InMemoryDataSource and got: IterableSQLDataSource", thrown.getMessage());
	}
}
