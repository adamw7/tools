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

	@Test
	public void equalToItself() {
		Result result = new Result(true, new String[] { "year1" });

		assertTrue(result.equals(result));
	}

	@Test
	public void notEqualToNull() {
		Result result = new Result(true, new String[] { "year1" });

		assertFalse(result.equals(null));
	}

	@Test
	public void notEqualToDifferentType() {
		Result result = new Result(true, new String[] { "year1" });

		assertFalse(result.equals("year1"));
	}

	@Test
	public void differentUniquenessIsNotEqual() {
		Result unique = new Result(true, new String[] { "year1" });
		Result notUnique = new Result(false, new String[] { "year1" });

		assertNotEquals(unique, notUnique);
	}

	@Test
	public void differentBetterOptionsAreNotEqual() {
		Set<Result> options = new HashSet<>();
		options.add(new Result(true, new String[] { "year1" }));
		Result withOptions = new Result(true, new String[] { "income" }, null, options);
		Result withoutOptions = new Result(true, new String[] { "income" });

		assertNotEquals(withOptions, withoutOptions);
	}

	@Test
	public void differentColumnsHaveDifferentHashCodes() {
		Result first = new Result(true, new String[] { "year1", "hlpi_name" });
		Result second = new Result(true, new String[] { "year1", "income" });

		assertNotEquals(first.hashCode(), second.hashCode());
	}

	@Test
	public void unequalResultsHaveDifferentHashCodes() {
		Result unique = new Result(true, new String[] { "year1" });
		Result notUnique = new Result(false, new String[] { "year1" });

		assertNotEquals(unique.hashCode(), notUnique.hashCode());
	}

	@Test
	public void nullColumnsEqualOnlyOtherNullColumns() {
		Result firstNull = new Result(true, null);
		Result secondNull = new Result(true, null);
		Result withColumns = new Result(true, new String[] { "year1" });

		assertEquals(firstNull, secondNull);
		assertNotEquals(firstNull, withColumns);
		assertNotEquals(withColumns, firstNull);
	}

	@Test
	public void nullColumnsContributeZeroToHashCode() {
		Result firstNull = new Result(true, null);
		Result secondNull = new Result(true, null);

		assertEquals(firstNull.hashCode(), secondNull.hashCode());
	}

	@Test
	public void toStringRendersAllFields() {
		Result result = new Result(true, new String[] { "year1" }, new String[] { "2020" });

		String text = result.toString();

		assertTrue(text.contains("unique=true"), text);
		assertTrue(text.contains("year1"), text);
		assertTrue(text.contains("2020"), text);
		assertTrue(text.contains("betterOptions="), text);
	}
}
