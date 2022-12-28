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
	
	static Stream<Arguments> happyPathNotUnique = Stream.of(
			  Arguments.of(NoMemoryUniquenessCheck.class, createDataSource(getHouseholdFile(), 1)),
			  Arguments.of(InMemoryUniquenessCheck.class, createInMemoryDataSource(getHouseholdFile(), 1))
			);

	private static IterableDataSource createDataSource(String file, int columnsRow) {
		try {
			IterableDataSource source = new CSVDataSource(file, columnsRow);
			return source;
		} catch (Exception e) {
			return null;
		}
	}

	private static InMemoryCSVDataSource createInMemoryDataSource(String file, int columnsRow) {
		try {
			InMemoryCSVDataSource source = new InMemoryCSVDataSource(file, columnsRow);
			return source;
		} catch (Exception e) {
			return null;
		}
	}

	@ParameterizedTest
	@VariableSource("happyPathNotUnique")
	public void happyPathNotUnique(Class uniquenessClass, IterableDataSource source) throws Exception {
		Uniqueness uniqueness = initUniquenessCheck(uniquenessClass, source);
		
		Result result;
		try {
			result = uniqueness.exec("year");
			assertEquals(result.isUnique(), false);
		} catch (Exception e) {
			fail(e.getMessage());
		}
	}

	private Uniqueness initUniquenessCheck(Class uniquenessClass, IterableDataSource source) throws InstantiationException, IllegalAccessException, IllegalArgumentException, InvocationTargetException, NoSuchMethodException, SecurityException {
		Uniqueness uniqueness = (Uniqueness) uniquenessClass.getConstructor().newInstance();
		
		if (uniqueness instanceof NoMemoryUniquenessCheck) {
			((NoMemoryUniquenessCheck) uniqueness).setDataSource(source);
		} else if (uniqueness instanceof InMemoryUniquenessCheck) {
			((InMemoryUniquenessCheck) uniqueness).setDataSource((InMemoryCSVDataSource)source);
		} else {
			throw new RuntimeException("Unknown type: " + uniqueness);
		}
		return uniqueness;
	}

	@Test
	public void happyPathUnique() throws FileNotFoundException {
		Uniqueness check = new NoMemoryUniquenessCheck(new CSVDataSource(getHouseholdFile(), 1));
		Result result;
		try {
			result = check.exec("year", "hlpi_name");
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
