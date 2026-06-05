package io.github.adamw7.tools.data.uniqueness;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;
import java.util.Set;

import org.junit.jupiter.api.Test;

public class ResultTest {

	@Test
	public void equalsIgnoresColumnOrder() {
		Result first = new Result(true, new String[] { "year1", "hlpi_name" });
		Result second = new Result(true, new String[] { "hlpi_name", "year1" });

		assertEquals(first, second);
		assertEquals(second, first);
	}

	@Test
	public void hashCodeIgnoresColumnOrder() {
		Result first = new Result(true, new String[] { "year1", "hlpi_name", "income" });
		Result second = new Result(true, new String[] { "income", "year1", "hlpi_name" });

		assertEquals(first.hashCode(), second.hashCode());
	}

	@Test
	public void differentColumnOrderResultsCollapseInSet() {
		Set<Result> set = new HashSet<>();
		set.add(new Result(true, new String[] { "year1", "hlpi_name" }));
		set.add(new Result(true, new String[] { "hlpi_name", "year1" }));

		assertEquals(1, set.size());
	}

	@Test
	public void differentColumnsAreNotEqual() {
		Result first = new Result(true, new String[] { "year1", "hlpi_name" });
		Result second = new Result(true, new String[] { "year1", "income" });

		assertNotEquals(first, second);
	}

	@Test
	public void nestedBetterOptionsIgnoreColumnOrder() {
		Set<Result> firstOptions = new HashSet<>();
		firstOptions.add(new Result(true, new String[] { "year1", "hlpi_name" }));
		Result first = new Result(true, new String[] { "income", "year1" }, null, firstOptions);

		Set<Result> secondOptions = new HashSet<>();
		secondOptions.add(new Result(true, new String[] { "hlpi_name", "year1" }));
		Result second = new Result(true, new String[] { "year1", "income" }, null, secondOptions);

		assertEquals(first, second);
		assertEquals(first.hashCode(), second.hashCode());
	}

	@Test
	public void orderIsIgnoredButRowStillMatters() {
		Result first = new Result(false, new String[] { "year1", "hlpi_name" }, new String[] { "2020", "a" });
		Result sameRowDifferentOrder = new Result(false, new String[] { "hlpi_name", "year1" },
				new String[] { "2020", "a" });
		Result differentRow = new Result(false, new String[] { "hlpi_name", "year1" }, new String[] { "2021", "a" });

		assertTrue(first.equals(sameRowDifferentOrder));
		assertFalse(first.equals(differentRow));
	}
}
