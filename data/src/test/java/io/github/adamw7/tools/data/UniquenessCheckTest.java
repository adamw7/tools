package io.github.adamw7.tools.data;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;

import java.io.FileNotFoundException;

import org.junit.jupiter.api.Test;

import io.github.adamw7.tools.data.UniquenessCheck.Result;

public class UniquenessCheckTest {

	
	@Test
	public void happyPathNotUnique() throws FileNotFoundException {
		UniquenessCheck check = new UniquenessCheck();
		check.setDataSource(new CSVDataSource(getHouseholdFile(), 1));
		Result result;
		try {
			result = check.exec("year");
			assertEquals(result.unique, false);
		} catch (Exception e) {
			fail(e.getMessage());
		}
	}
	
	@Test
	public void happyPathUnique() throws FileNotFoundException {
		UniquenessCheck check = new UniquenessCheck();
		check.setDataSource(new CSVDataSource(getHouseholdFile(), 1));
		Result result;
		try {
			result = check.exec("year", "hlpi_name");
			assertEquals(result.unique, true);
		} catch (Exception e) {
			fail(e.getMessage());
		}
	}

	private String getHouseholdFile() {
		return Utils.getFileName("Household-living-costs-price-indexes-September-2022-quarter-group-facts.csv");
	}
}
