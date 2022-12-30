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
import io.github.adamw7.tools.data.source.interfaces.IterableDataSource;
import io.github.adamw7.tools.data.uniqueness.ColumnNotFoundException;
import io.github.adamw7.tools.data.uniqueness.InMemoryUniquenessCheck;
import io.github.adamw7.tools.data.uniqueness.NoMemoryUniquenessCheck;
import io.github.adamw7.tools.data.uniqueness.Result;
import io.github.adamw7.tools.data.uniqueness.Uniqueness;

public class UniquenessCheckTest {

	private static final String[] NOT_UNIQUE_COLUMNS = new String[] { "year" };
	private static final String[] UNIQUE_COLUMNS = new String[] { "year", "hlpi_name" };

	static Stream<Arguments> happyPath() {
		return Stream.of(
				Arguments.of(NoMemoryUniquenessCheck.class, Utils.createDataSource(Utils.getHouseholdFile(), 1)),
				Arguments.of(InMemoryUniquenessCheck.class, Utils.createInMemoryDataSource(Utils.getHouseholdFile(), 1)));
	}

	@ParameterizedTest
	@VariableSource("happyPath")
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
	@VariableSource("happyPath")
	public void happyPathUnique(Class<Uniqueness> uniquenessClass, IterableDataSource source)
			throws Exception {
		Uniqueness uniqueness = initUniquenessCheck(uniquenessClass, source);
		
		try {
			Result result = uniqueness.exec(UNIQUE_COLUMNS);
			assertEquals(result.isUnique(), true);
		} catch (Exception e) {
			fail(e.getMessage());
		}
	}

	@ParameterizedTest
	@VariableSource("happyPath")
	public void happyPathUniqueShouldFindBetterOptions(Class<Uniqueness> uniquenessClass, IterableDataSource source) throws Exception {
		Uniqueness uniqueness = initUniquenessCheck(uniquenessClass, source);
		try {
			Result result = uniqueness.exec("year", "hlpi_name", "income");
			assertEquals(result.isUnique(), true);
			assertEquals(3, result.getBetterOptions().size());
		} catch (Exception e) {
			fail(e.getMessage());
		}
	}

	@ParameterizedTest
	@VariableSource("happyPath")
	void negativeWrongColumn(Class<Uniqueness> uniquenessClass, IterableDataSource source) throws Exception {
		Uniqueness uniqueness = initUniquenessCheck(uniquenessClass, source);
		String columnName = "notExistingColumn";
		ColumnNotFoundException thrown = assertThrows(ColumnNotFoundException.class, () -> {
			uniqueness.exec(columnName);
		}, "Expected exec method to throw, but it didn't");

		assertEquals(columnName
				+ " cannot be found in [hlpi_name, year, hlpi, tot_hhs, own, own_wm, own_prop, own_wm_prop, prop_hhs, age, size, income, expenditure, eqv_income, eqv_exp]",
				thrown.getMessage());
	}

	@ParameterizedTest
	@VariableSource("happyPath")
	void negativeEmptyInputArray(Class<Uniqueness> uniquenessClass, IterableDataSource source) throws Exception {
		Uniqueness uniqueness = initUniquenessCheck(uniquenessClass, source);
		IllegalArgumentException thrown = assertThrows(IllegalArgumentException.class, () -> {
			uniqueness.exec(new String[] {});
		}, "Expected exec method to throw, but it didn't");

		assertEquals("Wrong input: []", thrown.getMessage());
	}

	@ParameterizedTest
	@VariableSource("happyPath")
	void negativeNullsInInputArray(Class<Uniqueness> uniquenessClass, IterableDataSource source) throws Exception {
		Uniqueness uniqueness = initUniquenessCheck(uniquenessClass, source);
		IllegalArgumentException thrown = assertThrows(IllegalArgumentException.class, () -> {
			uniqueness.exec(new String[] { "hlpi_name", null, "year" });
		}, "Expected exec method to throw, but it didn't");

		assertEquals("Input columns cannot be null", thrown.getMessage());
	}
}
