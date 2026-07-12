package io.github.adamw7.tools.data.uniqueness;

import static org.junit.jupiter.api.Assertions.assertFalse;
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

		Key key = finder.key(new String[] { "x", "y", "z" }, new int[] { 2, 0 });

		assertTrue(key.equals(new Key(new String[] { "z", "x" })));
	}
}
