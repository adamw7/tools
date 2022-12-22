package io.github.adamw7.tools.data;


import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;

import org.junit.jupiter.api.Test;

import io.github.adamw7.tools.data.interfaces.DataSource;

public class CSVDataSourceTest {

	@Test
	public void happyPathNoColumns() {
		try (DataSource source = new CSVDataSource(Utils.getFileName("addresses.csv"))) {
			source.open();
			
			int i = 0;
			while (source.hasMoreData()) {
				String[] row = source.nextRow();
				if (row != null) {
					++i;
					assertEquals(6, row.length);					
				}
			}
			assertEquals(4, i);
		} catch (Exception e) {
			fail(e.getMessage());
		}
	}
	
	@Test
	public void happyPathWithColumns() {
		try (DataSource source = new CSVDataSource(householdFile(), 1)) {
			source.open();
			
			int i = 0;
			while (source.hasMoreData()) {
				String[] row = source.nextRow();
				if (row != null) {
					++i;
					assertEquals(15, row.length);					
				}
			}
			assertEquals(70, i);
		} catch (Exception e) {
			fail(e.getMessage());
		}
	}

	private String householdFile() {
		return Utils.getFileName("Household-living-costs-price-indexes-September-2022-quarter-group-facts.csv");
	}
	
}
