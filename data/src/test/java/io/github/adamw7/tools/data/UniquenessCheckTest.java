package io.github.adamw7.tools.data;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.fail;

import java.io.FileNotFoundException;
import java.lang.reflect.InvocationTargetException;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;

import io.github.adamw7.tools.data.source.CSVDataSource;
import io.github.adamw7.tools.data.source.InMemoryCSVDataSource;
import io.github.adamw7.tools.data.source.interfaces.IterableDataSource;
import io.github.adamw7.tools.data.uniqueness.ColumnNotFoundException;
import io.github.adamw7.tools.data.uniqueness.InMemoryUniquenessCheck;
import io.github.adamw7.tools.data.uniqueness.NoMemoryUniquenessCheck;
import io.github.adamw7.tools.data.uniqueness.Result;
import io.github.adamw7.tools.data.uniqueness.Uniqueness;

public class UniquenessCheckTest {

	private static final String[] NOT_UNIQUE_COLUMNS = new String[] { "year" };
	private static final String[] UNIQUE_COLUMNS = new String[] { "year", "hlpi_name" };

	static Stream<Arguments> happyPathNotUnique() {
		return Stream.of(
				Arguments.of(NoMemoryUniquenessCheck.class, createDataSource(getHouseholdFile(), 1),
						NOT_UNIQUE_COLUMNS),
				Arguments.of(InMemoryUniquenessCheck.class, createInMemoryDataSource(getHouseholdFile(), 1),
						NOT_UNIQUE_COLUMNS));
	}

	static Stream<Arguments> happyPathUnique() {
		return Stream.of(
				Arguments.of(NoMemoryUniquenessCheck.class, createDataSource(getHouseholdFile(), 1), UNIQUE_COLUMNS),
				Arguments.of(InMemoryUniquenessCheck.class, createInMemoryDataSource(getHouseholdFile(), 1),
						UNIQUE_COLUMNS));
	}

	private static IterableDataSource createDataSource(String file, int columnsRow) {
		try {
			return new CSVDataSource(file, columnsRow);
		} catch (Exception e) {
			return null;
		}
	}

	private static InMemoryCSVDataSource createInMemoryDataSource(String file, int columnsRow) {
		try {
			return new InMemoryCSVDataSource(file, columnsRow);
		} catch (Exception e) {
			return null;
		}
	}

	@ParameterizedTest
	@VariableSource("happyPathNotUnique")
	public void happyPathNotUnique(Class<Uniqueness> uniquenessClass, IterableDataSource source, String[] columns)
			throws Exception {
		Uniqueness uniqueness = initUniquenessCheck(uniquenessClass, source);

		Result result;
		try {
			result = uniqueness.exec(columns);
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
	@VariableSource("happyPathUnique")
	public void happyPathUnique(Class<Uniqueness> uniquenessClass, IterableDataSource source, String[] columns)
			throws Exception {
		Uniqueness uniqueness = initUniquenessCheck(uniquenessClass, source);

		Result result;
		try {
			result = uniqueness.exec(columns);
			assertEquals(result.isUnique(), true);
		} catch (Exception e) {
			fail(e.getMessage());
		}
	}

	@Test
	public void happyPathUniqueShouldFindBetterOptions() throws FileNotFoundException {
		Uniqueness check = new NoMemoryUniquenessCheck(new CSVDataSource(getHouseholdFile(), 1));
		Result result;
		try {
			result = check.exec("year", "hlpi_name", "income");
			assertEquals(result.isUnique(), true);
			assertEquals(3, result.getBetterOptions().size());
		} catch (Exception e) {
			fail(e.getMessage());
		}
	}

	@Test
	void negativeWrongColumn() throws FileNotFoundException {
		Uniqueness check = new NoMemoryUniquenessCheck(new CSVDataSource(getHouseholdFile(), 1));
		String columnName = "notExistingColumn";
		ColumnNotFoundException thrown = assertThrows(ColumnNotFoundException.class, () -> {
			check.exec(columnName);
		}, "Expected exec method to throw, but it didn't");

		assertEquals(columnName
				+ " cannot be found in [hlpi_name, year, hlpi, tot_hhs, own, own_wm, own_prop, own_wm_prop, prop_hhs, age, size, income, expenditure, eqv_income, eqv_exp]",
				thrown.getMessage());
	}

	@Test
	void negativeEmptyInputArray() throws FileNotFoundException {
		Uniqueness check = new NoMemoryUniquenessCheck(new CSVDataSource(getHouseholdFile(), 1));
		IllegalArgumentException thrown = assertThrows(IllegalArgumentException.class, () -> {
			check.exec(new String[] {});
		}, "Expected exec method to throw, but it didn't");

		assertEquals("Wrong input: []", thrown.getMessage());
	}

	@Test
	void negativeNullsInInputArray() throws FileNotFoundException {
		Uniqueness check = new NoMemoryUniquenessCheck(new CSVDataSource(getHouseholdFile(), 1));
		IllegalArgumentException thrown = assertThrows(IllegalArgumentException.class, () -> {
			check.exec(new String[] { "hlpi_name", null, "year" });
		}, "Expected exec method to throw, but it didn't");

		assertEquals("Input columns cannot be null", thrown.getMessage());
	}

	private static String getHouseholdFile() {
		return Utils.getFileName("Household-living-costs-price-indexes-September-2022-quarter-group-facts.csv");
	}
}
