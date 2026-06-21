package io.github.adamw7.tools.data.uniqueness;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;
import java.util.Set;

import org.junit.jupiter.api.Test;

public class KeyTest {

	@Test
	public void equalForSameContent() {
		Key first = new Key(new String[] { "a", "b" });
		Key second = new Key(new String[] { "a", "b" });

		assertEquals(first, second);
	}

	@Test
	public void equalToItself() {
		Key key = new Key(new String[] { "a", "b" });

		assertTrue(key.equals(key));
	}

	@Test
	public void notEqualToNull() {
		Key key = new Key(new String[] { "a" });

		assertFalse(key.equals(null));
	}

	@Test
	public void notEqualToDifferentType() {
		Key key = new Key(new String[] { "a" });

		assertFalse(key.equals("a"));
	}

	@Test
	public void notEqualForDifferentContent() {
		Key first = new Key(new String[] { "a", "b" });
		Key second = new Key(new String[] { "a", "c" });

		assertNotEquals(first, second);
	}

	@Test
	public void equalKeysShareHashCode() {
		Key first = new Key(new String[] { "x", "y" });
		Key second = new Key(new String[] { "x", "y" });

		assertEquals(first.hashCode(), second.hashCode());
	}

	@Test
	public void distinctKeysHaveDistinctHashCodes() {
		Key first = new Key(new String[] { "x", "y" });
		Key second = new Key(new String[] { "x", "z" });

		assertNotEquals(first.hashCode(), second.hashCode());
	}

	@Test
	public void toStringRendersValues() {
		Key key = new Key(new String[] { "a", "b" });

		assertEquals("[a, b]", key.toString());
	}

	@Test
	public void equalKeysCollapseInSet() {
		Set<Key> set = new HashSet<>();
		set.add(new Key(new String[] { "a", "b" }));
		set.add(new Key(new String[] { "a", "b" }));

		assertEquals(1, set.size());
	}

	@Test
	public void distinctKeysStayApartInSet() {
		Set<Key> set = new HashSet<>();
		set.add(new Key(new String[] { "a", "b" }));
		set.add(new Key(new String[] { "a", "c" }));

		assertEquals(2, set.size());
	}
}
