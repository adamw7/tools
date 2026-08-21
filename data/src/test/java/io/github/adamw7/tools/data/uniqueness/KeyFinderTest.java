package io.github.adamw7.tools.data.uniqueness;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

public class KeyFinderTest {

	@Test
	public void nullRowIsNeverFound() {
		KeyFinder finder = new KeyFinder(new int[] { 0 });

		assertFalse(finder.found(null));
	}

	@Test
	public void firstRowIsNotADuplicate() {
		KeyFinder finder = new KeyFinder(new int[] { 0 });

		assertFalse(finder.found(new String[] { "a", "b" }));
	}

	@Test
	public void repeatedRowIsADuplicate() {
		KeyFinder finder = new KeyFinder(new int[] { 0 });

		finder.found(new String[] { "a", "b" });

		assertTrue(finder.found(new String[] { "a", "b" }));
	}

	@Test
	public void onlyConfiguredColumnsFormTheKey() {
		KeyFinder finder = new KeyFinder(new int[] { 0 });

		finder.found(new String[] { "a", "b" });

		assertTrue(finder.found(new String[] { "a", "different" }));
	}

	@Test
	public void differenceInUncheckedColumnDoesNotCollideOnCheckedColumn() {
		KeyFinder finder = new KeyFinder(new int[] { 1 });

		finder.found(new String[] { "a", "b" });

		assertFalse(finder.found(new String[] { "a", "c" }));
	}

	@Test
	public void multiColumnKeyMatchesOnAllConfiguredColumns() {
		KeyFinder finder = new KeyFinder(new int[] { 0, 2 });

		finder.found(new String[] { "a", "b", "c" });

		assertTrue(finder.found(new String[] { "a", "ignored", "c" }));
	}

	@Test
	public void multiColumnKeyDiffersWhenAnyConfiguredColumnDiffers() {
		KeyFinder finder = new KeyFinder(new int[] { 0, 2 });

		finder.found(new String[] { "a", "b", "c" });

		assertFalse(finder.found(new String[] { "a", "b", "different" }));
	}

	@Test
	public void distinctRowsAreNotDuplicates() {
		KeyFinder finder = new KeyFinder(new int[] { 0 });

		assertFalse(finder.found(new String[] { "a" }));
		assertFalse(finder.found(new String[] { "b" }));
		assertFalse(finder.found(new String[] { "c" }));
	}

	@Test
	public void keyProjectsConfiguredColumnsInOrder() {
		KeyFinder finder = new KeyFinder(new int[] { 2, 0 });

		Key key = finder.key(new String[] { "x", "y", "z" });

		assertTrue(key.equals(new Key(new String[] { "z", "x" })));
	}

	/**
	 * The indices come from the header, so a row that arrives shorter than the file
	 * declares used to fail with a bare ArrayIndexOutOfBoundsException naming neither the
	 * row nor the column.
	 */
	@Test
	public void shortRowIsReportedAsRagged() {
		KeyFinder finder = new KeyFinder(new int[] { 2 });

		RaggedRowException thrown = assertThrows(RaggedRowException.class,
				() -> finder.found(new String[] { "a", "b" }));

		assertEquals("Data row 1 is ragged: the key reads at least 3 columns but the row has 2: [a, b]",
				thrown.getMessage());
	}

	@Test
	public void raggedRowIsReportedByItsPositionArityAndContent() {
		KeyFinder finder = new KeyFinder(new int[] { 0, 3 });

		RaggedRowException thrown = assertThrows(RaggedRowException.class,
				() -> finder.found(new String[] { "a" }));

		assertEquals(1, thrown.getRowNumber());
		assertEquals(4, thrown.getExpectedColumns());
		assertEquals(1, thrown.getActualColumns());
	}

	/** The highest index the key reads decides the arity, not the number of columns in it. */
	@Test
	public void arityIsTheHighestIndexTheKeyReads() {
		KeyFinder finder = new KeyFinder(new int[] { 4 });

		RaggedRowException thrown = assertThrows(RaggedRowException.class,
				() -> finder.found(new String[] { "a", "b", "c", "d" }));

		assertEquals(5, thrown.getExpectedColumns());
	}

	@Test
	public void raggedRowIsNumberedAmongTheRowsRead() {
		KeyFinder finder = new KeyFinder(new int[] { 1 });

		finder.found(new String[] { "a", "b" });
		finder.found(new String[] { "c", "d" });

		RaggedRowException thrown = assertThrows(RaggedRowException.class,
				() -> finder.found(new String[] { "e" }));

		assertEquals(3, thrown.getRowNumber());
	}

	/** A row the source skipped is no row of the data, so it is not counted as one. */
	@Test
	public void skippedRowsAreNotNumbered() {
		KeyFinder finder = new KeyFinder(new int[] { 1 });

		finder.found(null);

		RaggedRowException thrown = assertThrows(RaggedRowException.class,
				() -> finder.found(new String[] { "a" }));

		assertEquals(1, thrown.getRowNumber());
	}

	/** Only a row too short to project is a fault; trailing columns the key ignores are not. */
	@Test
	public void longerRowThanTheKeyReadsIsAccepted() {
		KeyFinder finder = new KeyFinder(new int[] { 0 });

		assertFalse(finder.found(new String[] { "a", "b", "c" }));
	}

	@Test
	public void rowOfExactlyTheKeyArityIsAccepted() {
		KeyFinder finder = new KeyFinder(new int[] { 2 });

		assertFalse(finder.found(new String[] { "a", "b", "c" }));
	}
}
