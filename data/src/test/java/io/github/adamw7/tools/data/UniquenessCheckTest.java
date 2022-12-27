package io.github.adamw7.tools.data;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.fail;

import java.io.FileNotFoundException;

import org.junit.jupiter.api.Test;

import io.github.adamw7.tools.data.source.CSVDataSource;
import io.github.adamw7.tools.data.uniqueness.ColumnNotFoundException;
import io.github.adamw7.tools.data.uniqueness.Result;
import io.github.adamw7.tools.data.uniqueness.UniquenessCheck;

public class UniquenessCheckTest {

	@Test
	public void happyPathNotUnique() throws FileNotFoundException {
		UniquenessCheck check = new UniquenessCheck(new CSVDataSource(getHouseholdFile(), 1));
		Result result;
		try {
			result = check.exec("year");
			assertEquals(result.isUnique(), false);
		} catch (Exception e) {
			fail(e.getMessage());
		}
	}

	@Test
	public void happyPathUnique() throws FileNotFoundException {
		UniquenessCheck check = new UniquenessCheck(new CSVDataSource(getHouseholdFile(), 1));
		Result result;
		try {
			result = check.exec("year", "hlpi_name");
			assertEquals(result.isUnique(), true);
		} catch (Exception e) {
			fail(e.getMessage());
		}
	}

	@Test
	void negativeWrongColumn() throws FileNotFoundException {
		UniquenessCheck check = new UniquenessCheck(new CSVDataSource(getHouseholdFile(), 1));
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
		UniquenessCheck check = new UniquenessCheck(new CSVDataSource(getHouseholdFile(), 1));
		IllegalArgumentException thrown = assertThrows(IllegalArgumentException.class, () -> {
			check.exec(new String[] {});
		}, "Expected exec method to throw, but it didn't");

		assertEquals("Wrong input: []", thrown.getMessage());
	}
	
	@Test
	void negativeNullsInInputArray() throws FileNotFoundException {
		UniquenessCheck check = new UniquenessCheck(new CSVDataSource(getHouseholdFile(), 1));
		IllegalArgumentException thrown = assertThrows(IllegalArgumentException.class, () -> {
			check.exec(new String[] {"hlpi_name", null, "year"});
		}, "Expected exec method to throw, but it didn't");

		assertEquals("Input columns cannot be null", thrown.getMessage());
	}

	private String getHouseholdFile() {
		return Utils.getFileName("Household-living-costs-price-indexes-September-2022-quarter-group-facts.csv");
	}
}
