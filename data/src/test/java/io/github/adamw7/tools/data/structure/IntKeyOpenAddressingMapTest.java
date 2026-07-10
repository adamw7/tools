package io.github.adamw7.tools.data.structure;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import java.util.Collection;

import org.junit.jupiter.api.Test;

public class IntKeyOpenAddressingMapTest {

	@Test
	public void happyPath() {
		IntKeyOpenAddressingMap<String> map = new IntKeyOpenAddressingMap<>();
		assertNull(map.put(1, "A"));
		assertNull(map.put(2, "B"));
		assertEquals(2, map.size());
		assertEquals("A", map.get(1));
		assertEquals("B", map.get(2));
	}

	@Test
	public void overwriteReturnsPreviousValue() {
		IntKeyOpenAddressingMap<String> map = new IntKeyOpenAddressingMap<>();
		map.put(1, "A");
		assertEquals("A", map.put(1, "B"));
		assertEquals(1, map.size());
		assertEquals("B", map.get(1));
	}

	@Test
	public void getMissingKeyReturnsNull() {
		IntKeyOpenAddressingMap<String> map = new IntKeyOpenAddressingMap<>();
		assertNull(map.get(42));
	}

	@Test
	public void getOrDefault() {
		IntKeyOpenAddressingMap<String> map = new IntKeyOpenAddressingMap<>();
		map.put(1, "A");
		assertEquals("A", map.getOrDefault(1, "fallback"));
		assertEquals("fallback", map.getOrDefault(2, "fallback"));
	}

	@Test
	public void containsKey() {
		IntKeyOpenAddressingMap<String> map = new IntKeyOpenAddressingMap<>();
		map.put(1, "A");
		assertTrue(map.containsKey(1));
		assertFalse(map.containsKey(2));
	}

	@Test
	public void nullValueIsStoredAndReportedByContainsKey() {
		IntKeyOpenAddressingMap<String> map = new IntKeyOpenAddressingMap<>();
		map.put(7, null);
		assertTrue(map.containsKey(7));
		assertNull(map.get(7));
		assertEquals(1, map.size());
	}

	@Test
	public void containsValue() {
		IntKeyOpenAddressingMap<String> map = new IntKeyOpenAddressingMap<>();
		map.put(1, "A");
		map.put(2, "B");
		assertTrue(map.containsValue("A"));
		assertFalse(map.containsValue("C"));
	}

	@Test
	public void removeReturnsValueAndShrinks() {
		IntKeyOpenAddressingMap<String> map = new IntKeyOpenAddressingMap<>();
		map.put(1, "A");
		map.put(2, "B");
		assertEquals("A", map.remove(1));
		assertNull(map.get(1));
		assertEquals("B", map.get(2));
		assertEquals(1, map.size());
	}

	@Test
	public void removeMissingKeyReturnsNull() {
		IntKeyOpenAddressingMap<String> map = new IntKeyOpenAddressingMap<>();
		map.put(1, "A");
		assertNull(map.remove(999));
		assertEquals(1, map.size());
	}

	@Test
	public void liveEntryFoundPastTombstone() {
		// In the default capacity-64 table (prime 61) the keys 0, 61 and 122 all
		// begin probing at the same slot, so they share a probe chain. Removing
		// key 0 leaves a tombstone that 61 and 122 must probe past to be found.
		IntKeyOpenAddressingMap<String> map = new IntKeyOpenAddressingMap<>();
		map.put(0, "A");
		map.put(61, "B");
		map.put(122, "C");
		assertEquals("A", map.remove(0));
		assertEquals("B", map.get(61));
		assertEquals("C", map.get(122));
		assertNull(map.get(0));
		assertEquals(2, map.size());
	}

	@Test
	public void negativeKeysAreSupported() {
		IntKeyOpenAddressingMap<String> map = new IntKeyOpenAddressingMap<>();
		map.put(-1, "minus one");
		map.put(Integer.MIN_VALUE, "min");
		assertEquals("minus one", map.get(-1));
		assertEquals("min", map.get(Integer.MIN_VALUE));
	}

	@Test
	public void resizeKeepsAllEntries() {
		IntKeyOpenAddressingMap<String> map = new IntKeyOpenAddressingMap<>();
		int count = 500; // forces multiple resizes from the default capacity
		for (int i = 0; i < count; ++i) {
			map.put(i, String.valueOf(i));
		}
		assertEquals(count, map.size());
		for (int i = 0; i < count; ++i) {
			assertEquals(String.valueOf(i), map.get(i));
		}
	}

	@Test
	public void keysReturnsEveryLiveKey() {
		IntKeyOpenAddressingMap<String> map = new IntKeyOpenAddressingMap<>();
		for (int i = 0; i < 100; ++i) {
			map.put(i, String.valueOf(i));
		}
		map.remove(50);
		int[] keys = map.keys();
		Arrays.sort(keys);
		assertEquals(99, keys.length);
		assertTrue(Arrays.binarySearch(keys, 0) >= 0);
		assertFalse(Arrays.binarySearch(keys, 50) >= 0);
	}

	@Test
	public void valuesReturnsEveryLiveValue() {
		IntKeyOpenAddressingMap<String> map = new IntKeyOpenAddressingMap<>();
		for (int i = 0; i < 50; ++i) {
			map.put(i, String.valueOf(i));
		}
		Collection<String> values = map.values();
		assertEquals(50, values.size());
		for (int i = 0; i < 50; ++i) {
			assertTrue(values.contains(String.valueOf(i)), i + " is missing in values");
		}
	}

	@Test
	public void clearEmptiesTheMap() {
		IntKeyOpenAddressingMap<String> map = new IntKeyOpenAddressingMap<>();
		for (int i = 0; i < 100; ++i) {
			map.put(i, String.valueOf(i));
		}
		map.clear();
		assertEquals(0, map.size());
		assertTrue(map.isEmpty());
		assertNull(map.get(0));
	}

	@Test
	public void clearOnEmptyMapDoesNotThrow() {
		IntKeyOpenAddressingMap<String> map = new IntKeyOpenAddressingMap<>();
		map.clear();
		assertEquals(0, map.size());
	}

	@Test
	public void reuseAfterClear() {
		IntKeyOpenAddressingMap<String> map = new IntKeyOpenAddressingMap<>();
		map.put(1, "A");
		map.clear();
		map.put(2, "B");
		assertNull(map.get(1));
		assertEquals("B", map.get(2));
		assertEquals(1, map.size());
	}

	@Test
	public void zeroSizeIsRejected() {
		IllegalArgumentException thrown = assertThrows(IllegalArgumentException.class,
				() -> new IntKeyOpenAddressingMap<>(0));
		assertEquals("Wrong size: 0", thrown.getMessage());
	}

	@Test
	public void negativeSizeIsRejected() {
		IllegalArgumentException thrown = assertThrows(IllegalArgumentException.class,
				() -> new IntKeyOpenAddressingMap<>(-10));
		assertEquals("Wrong size: -10", thrown.getMessage());
	}

	@Test
	public void emptyMap() {
		IntKeyOpenAddressingMap<String> map = new IntKeyOpenAddressingMap<>();
		assertTrue(map.isEmpty());
		assertEquals(0, map.size());
		assertEquals(0, map.keys().length);
		assertTrue(map.values().isEmpty());
	}

	@Test
	public void growsBeyondSmallInitialCapacity() {
		IntKeyOpenAddressingMap<String> map = new IntKeyOpenAddressingMap<>(3);
		for (int i = 0; i < 100; ++i) {
			map.put(i, "v" + i);
		}
		assertEquals(100, map.size());
		for (int i = 0; i < 100; ++i) {
			assertEquals("v" + i, map.get(i));
		}
	}
}
