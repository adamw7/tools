package io.github.adamw7.tools.data.uniqueness;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.lang.reflect.InvocationTargetException;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Stream;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import io.github.adamw7.tools.data.DBTest;
import io.github.adamw7.tools.data.Utils;
import io.github.adamw7.tools.data.source.interfaces.IterableDataSource;

public class UniquenessCheckTest extends DBTest {

	private static final String[] NOT_UNIQUE_COLUMNS = new String[] { "year1" };
	private static final String[] UNIQUE_COLUMNS = new String[] { "year1", "hlpi_name" };

	static Stream<Arguments> happyPath() throws Exception {
		String householdFile = Utils.getHouseholdFile();
		
		return Stream.of(
				Arguments.of(NoMemoryUniquenessCheck.class, Utils.createDataSource(householdFile, 1)),
				Arguments.of(InMemoryUniquenessCheck.class, Utils.createInMemoryDataSource(householdFile, 1)),
				Arguments.of(NoMemoryUniquenessCheck.class, Utils.createIterableSQLDataSource(connection, query)),
				Arguments.of(InMemoryUniquenessCheck.class, Utils.createInMemorySQLDataSource(connection, query))
				);
	}

	@ParameterizedTest
	@MethodSource("happyPath")
	public void happyPathNotUnique(Class<Uniqueness> uniquenessClass, IterableDataSource source)
			throws Exception {
		Uniqueness uniqueness = initUniquenessCheck(uniquenessClass, source);

		Result result;
		try {
			result = uniqueness.exec(NOT_UNIQUE_COLUMNS);
			assertEquals(result.isUnique(), false);
		} catch (Exception e) {
			fail(e.getMessage());
		}
	}

	private Uniqueness initUniquenessCheck(Class<Uniqueness> uniquenessClass, IterableDataSource source)
			throws InstantiationException, IllegalAccessException, IllegalArgumentException, InvocationTargetException,
			NoSuchMethodException, SecurityException {
		Uniqueness uniqueness = uniquenessClass.getConstructor().newInstance();
		uniqueness.setDataSource(source);
		return uniqueness;
	}

	@ParameterizedTest
	@MethodSource("happyPath")
	public void happyPathUnique(Class<Uniqueness> uniquenessClass, IterableDataSource source)
			throws Exception {
		Uniqueness uniqueness = initUniquenessCheck(uniquenessClass, source);
		
		try {
			Result result = uniqueness.exec(UNIQUE_COLUMNS);
			assertEquals(result.isUnique(), true);
		} catch (Exception e) {
			e.printStackTrace();
			fail(e.getMessage());
		}
	}

	@ParameterizedTest
	@MethodSource("happyPath")
	public void happyPathUniqueShouldFindBetterOptions(Class<Uniqueness> uniquenessClass, IterableDataSource source) throws Exception {
		Uniqueness uniqueness = initUniquenessCheck(uniquenessClass, source);
		try {
			Result result = uniqueness.exec("year1", "hlpi_name", "income");
			assertEquals(result.isUnique(), true);
			Set<Result> betterOptions = result.getBetterOptions();
			assertEquals(3, betterOptions.size());
			assertTrue(betterOptions.contains(new Result(true, new String[] {"year1", "hlpi_name"})));
			Set<Result> incomeOnly = new HashSet<>();
			incomeOnly.add(new Result(true, new String[] {"income"}));
			assertTrue(betterOptions.contains(new Result(true, new String[] {"income", "year1"}, null, incomeOnly)));	
			assertTrue(betterOptions.contains(new Result(true, new String[] {"income", "hlpi_name"}, null, incomeOnly)));	
		} catch (Exception e) {
			fail(e.getMessage());
		}
	}
	
	@ParameterizedTest
	@MethodSource("happyPath")
	public void happyPathUniqueShouldNotFindBetterOptions(Class<Uniqueness> uniquenessClass, IterableDataSource source) throws Exception {
		Uniqueness uniqueness = initUniquenessCheck(uniquenessClass, source);
		try {
			Result result = uniqueness.exec("income");
			assertEquals(result.isUnique(), true);
			assertEquals(0, result.getBetterOptions().size());
		} catch (Exception e) {
			fail(e.getMessage());
		}
	}

	@ParameterizedTest
	@MethodSource("happyPath")
	void negativeWrongColumn(Class<Uniqueness> uniquenessClass, IterableDataSource source) throws Exception {
		Uniqueness uniqueness = initUniquenessCheck(uniquenessClass, source);
		String columnName = "notExistingColumn";
		ColumnNotFoundException thrown = assertThrows(ColumnNotFoundException.class, () -> {
			uniqueness.exec(columnName);
		}, "Expected exec method to throw, but it didn't");
		String expectedMessage = columnName
				+ " cannot be found in [hlpi_name, year1, hlpi, tot_hhs, own, own_wm, own_prop, own_wm_prop, prop_hhs, age, size, income, expenditure, eqv_income, eqv_exp]";
		assertEquals(expectedMessage.toLowerCase(),
				thrown.getMessage().toLowerCase());
	}

	@ParameterizedTest
	@MethodSource("happyPath")
	void negativeEmptyInputArray(Class<Uniqueness> uniquenessClass, IterableDataSource source) throws Exception {
		Uniqueness uniqueness = initUniquenessCheck(uniquenessClass, source);
		IllegalArgumentException thrown = assertThrows(IllegalArgumentException.class, () -> {
			uniqueness.exec(new String[] {});
		}, "Expected exec method to throw, but it didn't");

		assertEquals("Wrong input: []", thrown.getMessage());
	}
	
	@ParameterizedTest
	@MethodSource("happyPath")
	void negativeNullInputArray(Class<Uniqueness> uniquenessClass, IterableDataSource source) throws Exception {
		Uniqueness uniqueness = initUniquenessCheck(uniquenessClass, source);
		IllegalArgumentException thrown = assertThrows(IllegalArgumentException.class, () -> {
			uniqueness.exec((String[])null);
		}, "Expected exec method to throw, but it didn't");

		assertEquals("Wrong input: null", thrown.getMessage());
	}

	@ParameterizedTest
	@MethodSource("happyPath")
	void negativeNullsInInputArray(Class<Uniqueness> uniquenessClass, IterableDataSource source) throws Exception {
		Uniqueness uniqueness = initUniquenessCheck(uniquenessClass, source);
		IllegalArgumentException thrown = assertThrows(IllegalArgumentException.class, () -> {
			uniqueness.exec(new String[] { "hlpi_name", null, "year1" });
		}, "Expected exec method to throw, but it didn't");

		assertEquals("Input columns cannot be null", thrown.getMessage());
	}
	
	@ParameterizedTest
	@MethodSource("happyPath")
	void negativeDuplicatesInInputArray(Class<Uniqueness> uniquenessClass, IterableDataSource source) throws Exception {
		Uniqueness uniqueness = initUniquenessCheck(uniquenessClass, source);
		IllegalArgumentException thrown = assertThrows(IllegalArgumentException.class, () -> {
			uniqueness.exec(new String[] { "year1", "year1" });
		}, "Expected exec method to throw, but it didn't");

		assertEquals("Duplicate in input: year1", thrown.getMessage());
	}
}
