package io.github.adamw7.tools.data.structure;

import static io.github.adamw7.tools.data.Utils.named;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.params.provider.Arguments.of;

import java.util.Collection;
import java.util.ConcurrentModificationException;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;


public class MapTest {
	private static final int CUSTOM_SIZE = 64;
	
	static Stream<Arguments> happyPathImplementations() {
		return Stream.of(of(named(new HashMap<Integer, String>(), 16)),
				of(named(new OpenAddressingMap<Integer, String>(), OpenAddressingMap.DEFAULT_SIZE)));
	}
	
	static Stream<Arguments> happyPathImplementationsWithCustomSize() {
		int size = CUSTOM_SIZE;
		return Stream.of(of(named(new HashMap<Integer, String>(size), size)),
				of(named(new OpenAddressingMap<Integer, String>(size), size)));
	}

	static Stream<Arguments> allImplementations() {
		return Stream.concat(happyPathImplementations(), 
				happyPathImplementationsWithCustomSize());
	}
	
	static Stream<Arguments> conflictingHashImplementations() {
		return Stream.of(of(named(new HashMap<ConflictingKey, String>())),
				of(named(new OpenAddressingMap<ConflictingKey, String>())));
	}
	
	@ParameterizedTest
	@MethodSource("allImplementations")
	public void happyPath(Map<Integer, String> map) {
		map.put(1, "A");
		assertEquals(1, map.size());
		assertEquals("A", map.get(1));
		map.put(2, "B");
		assertEquals(2, map.size());
		assertEquals("A", map.get(1));
		assertEquals("B", map.get(2));
	}

	@ParameterizedTest
	@MethodSource("allImplementations")
	public void overwrite(Map<Integer, String> map) {
		map.put(1, "A");
		assertEquals(1, map.size());
		assertEquals("A", map.get(1));
		map.put(1, "B");
		assertEquals(1, map.size());
		assertEquals("B", map.get(1));
	}

	@ParameterizedTest
	@MethodSource("allImplementations")
	public void putNewKeyReturnsNull(Map<Integer, String> map) {
		assertNull(map.put(1, "A"));
	}

	@ParameterizedTest
	@MethodSource("allImplementations")
	public void putExistingKeyReturnsPreviousValue(Map<Integer, String> map) {
		map.put(1, "A");
		assertEquals("A", map.put(1, "B"));
	}

	@ParameterizedTest
	@MethodSource("allImplementations")
	public void values(Map<Integer, String> map) {
		int size = 50;
		for (int i = 0; i < size; ++i) {
			map.put(i, String.valueOf(i));
		}

		Collection<String> values = map.values();
		assertEquals(size, values.size());
		for (int i = 0; i < size; ++i) {
			assertTrue(values.contains(String.valueOf(i)), i + " is missing in values");
		}
	}

	@ParameterizedTest
	@MethodSource("allImplementations")
	public void remove(Map<Integer, String> map) {
		map.put(1, "A");
		map.put(2, "B");
		map.remove(1);
		assertEquals(1, map.size());
		assertNull(map.get(1));
		assertEquals("B", map.get(2));
	}

	@ParameterizedTest
	@MethodSource("allImplementations")
	public void removeNonExistentKey(Map<Integer, String> map) {
		map.put(1, "A");
		assertNull(map.remove(999));
		assertEquals(1, map.size());
		assertEquals("A", map.get(1));
	}

	@ParameterizedTest
	@MethodSource("allImplementations")
	public void reinsertRemovedKey(Map<Integer, String> map) {
		map.put(1, "A");
		map.remove(1);
		assertNull(map.put(1, "B"));
		assertEquals(1, map.size());
		assertFalse(map.isEmpty());
		assertEquals("B", map.get(1));
		assertTrue(map.containsKey(1));
	}

	@ParameterizedTest
	@MethodSource("allImplementations")
	public void containsKey(Map<Integer, String> map) {
		map.put(1, "A");
		map.put(2, "B");
		assertTrue(map.containsKey(1));
		assertTrue(map.containsKey(2));
		assertFalse(map.containsKey(3));
	}

	@ParameterizedTest
	@MethodSource("allImplementations")
	public void containsValue(Map<Integer, String> map) {
		map.put(1, "A");
		map.put(2, "B");
		assertTrue(map.containsValue("A"));
		assertTrue(map.containsValue("B"));
		assertFalse(map.containsValue("C"));
	}

	@ParameterizedTest
	@MethodSource("allImplementations")
	public void keySet(Map<Integer, String> map) {
		int[] keys = new int[] {-1, 0, 1, 5, 1000};
		
		for (int key : keys) {
			map.put(key, String.valueOf(key));
		}
		Set<Integer> keySet = map.keySet();
		assertEquals(keys.length, keySet.size());
		for (int key : keys) {
			assertTrue(keySet.contains(key), key + " is missing in keys");
		}
	}
	
	@ParameterizedTest
	@MethodSource("allImplementations")
	public void putAll(Map<Integer, String> map) {
		final int size = 50;
		map.putAll(sampleMap(size));
		for (int i = 0; i < size; ++i) {
			String value = map.get(i);
			assertNotNull(value);
			assertEquals(value, String.valueOf(i));
		}
		assertEquals(size, map.size());
	}

	private Map<? extends Integer, ? extends String> sampleMap(int size) {
		Map<Integer, String> map = new HashMap<>();
		for (int i = 0; i < size; ++i) {
			map.put(i, String.valueOf(i));
		}
		return map;
	}
	
	@ParameterizedTest
	@MethodSource("allImplementations")
	public void clear(Map<Integer, String> map) {
		final int size = 50;
		map.putAll(sampleMap(size));
		map.clear();

		assertEquals(0, map.size());
		for (int i = 0; i < size; ++i) {
			String value = map.get(i);
			assertNull(value);
		}
	}
	
	@ParameterizedTest
	@MethodSource("allImplementations")
	public void entrySet(Map<Integer, String> map) {
		final int size = 50;
		map.putAll(sampleMap(size));
		Set<Entry<Integer, String>> entrySet = map.entrySet();
		assertEquals(size, entrySet.size());
		for (Entry<Integer, String> entry : entrySet) {
			assertEquals(map.get(entry.getKey()), entry.getValue());
		}
	}
	
	@ParameterizedTest
	@MethodSource("allImplementations")
	public void resize(Map<Integer, String> map) {
		final int size = OpenAddressingMap.DEFAULT_SIZE * 2; // forcing resize
		putData(map, size);
	}
	
	@ParameterizedTest
	@MethodSource("allImplementations")
	public void resizeVsRemovals(Map<Integer, String> map) {
		final int size = OpenAddressingMap.DEFAULT_SIZE * 2; // forcing resize
		int keyToRemove = -10000;
		String valueToRemove = "ItemToRemove";
		map.put(keyToRemove, valueToRemove);
		assertEquals(1, map.size());
		String removed = map.remove(keyToRemove);
		assertEquals(0, map.size());
		assertEquals(valueToRemove, removed);
		putData(map, size);
	}
	
	@ParameterizedTest
	@MethodSource("allImplementations")
	public void multipleResize(Map<Integer, String> map) {
		final int size = OpenAddressingMap.DEFAULT_SIZE * 4; // forcing resize
		putData(map, size);
	}

	private void checkValues(Map<Integer, String> map, final int size) {
		for (int i = 0; i < size; ++i) {
			String value = map.get(i);
			assertEquals(value, String.valueOf(i));
		}
	}
	
	@ParameterizedTest
	@MethodSource("allImplementations")
	public void customNonPrimeSize(Map<Integer, String> map) {
		int maxSize = CUSTOM_SIZE * 4;
		putData(map, maxSize);
		map.remove(5);
		assertEquals(maxSize - 1, map.size());
	}
	
	@ParameterizedTest
	@MethodSource("conflictingHashImplementations")
	public void conflicts(Map<ConflictingKey, String> map) {
		ConflictingKey key10 = new ConflictingKey(10, "10");
		String value10 = "Value10";
		map.put(key10, value10);
		ConflictingKey key12 = new ConflictingKey(12, "12");
		String value12 = "Value12";
		map.put(key12, value12);
		
		assertEquals(2, map.size());
		assertEquals(value10, map.get(key10));
		assertEquals(value12, map.get(key12));
		Set<ConflictingKey> keySet = map.keySet();
		assertTrue(keySet.contains(key10));
		assertTrue(keySet.contains(key12));

		Collection<String> values = map.values();
		assertTrue(values.contains(value10));
		assertTrue(values.contains(value12));
		
		map.clear();
		assertEquals(0, map.size());
	}
	
	@ParameterizedTest
	@MethodSource("conflictingHashImplementations")
	public void liveEntriesFoundPastTombstoneInProbeChain(Map<ConflictingKey, String> map) {
		ConflictingKey first = new ConflictingKey(1, "1");
		ConflictingKey middle = new ConflictingKey(2, "2");
		ConflictingKey last = new ConflictingKey(3, "3");
		map.put(first, "A");
		map.put(middle, "B");
		map.put(last, "C");

		assertEquals("B", map.remove(middle));

		assertNull(map.get(middle));
		assertEquals("A", map.get(first));
		assertEquals("C", map.get(last));
		assertEquals(2, map.size());
	}

	@ParameterizedTest
	@MethodSource("conflictingHashImplementations")
	public void missingKeyInProbeChainWithTombstoneReturnsNull(Map<ConflictingKey, String> map) {
		ConflictingKey present = new ConflictingKey(1, "1");
		ConflictingKey removed = new ConflictingKey(2, "2");
		map.put(present, "A");
		map.put(removed, "B");
		map.remove(removed);

		assertNull(map.get(new ConflictingKey(99, "99")));
		assertNull(map.remove(new ConflictingKey(99, "99")));
		assertEquals(1, map.size());
		assertEquals("A", map.get(present));
	}

	@ParameterizedTest
	@MethodSource("allImplementations")
	public void multipleClear(Map<Integer, String> map) {
		final int size = OpenAddressingMap.DEFAULT_SIZE * 5; // forcing resize
		for (int i = 1; i < 6; ++i) {
			putData(map, size * i);
			map.clear();
			assertEquals(0, map.size());	
		}	
	}

	private void putData(Map<Integer, String> map, final int size) {
		map.putAll(sampleMap(size));
		assertEquals(size, map.size());
		checkValues(map, size);
	}
	
	@Test
	public void negativeTooLowNumber() {
		IllegalArgumentException thrown = assertThrows(IllegalArgumentException.class, () -> new OpenAddressingMap<>(-10), "Expected constructor method to throw, but it didn't");

		assertEquals("Wrong size: -10",thrown.getMessage());
	}
	
	@Test
	public void zeroSizeIsRejected() {
		IllegalArgumentException thrown = assertThrows(IllegalArgumentException.class, () -> new OpenAddressingMap<>(0), "Expected constructor method to throw, but it didn't");

		assertEquals("Wrong size: 0", thrown.getMessage());
	}

	@Test
	public void nullKeyIsRejectedByPut() {
		NullPointerException thrown = assertThrows(NullPointerException.class, () -> new OpenAddressingMap<>(2).put(null, null), "Expected put method to throw, but it didn't");

		assertEquals("Key is null",thrown.getMessage());
	}

	@Test
	public void nullKeyIsAbsentRatherThanAnErrorForLookups() {
		// A map that cannot hold a null key must report it missing, not throw:
		// Map.containsKey/get/remove specify that for the key types they reject.
		Map<Integer, String> map = new OpenAddressingMap<>();
		map.put(1, "A");

		assertFalse(map.containsKey(null));
		assertNull(map.get(null));
		assertNull(map.remove(null));
		assertEquals(1, map.size());
	}
	
	@ParameterizedTest
	@MethodSource("allImplementations")
	public void containsKeyWhenValueIsNull(Map<Integer, String> map) {
		map.put(1, null);
		assertTrue(map.containsKey(1));
		assertNull(map.get(1));
		assertTrue(map.containsValue(null));
	}

	@ParameterizedTest
	@MethodSource("allImplementations")
	public void keySetDoesNotContainAnAbsentKey(Map<Integer, String> map) {
		map.put(1, "A");
		Set<Integer> keySet = map.keySet();
		assertTrue(keySet.contains(1));
		assertFalse(keySet.contains(2));
	}

	@ParameterizedTest
	@MethodSource("allImplementations")
	public void keySetIsALiveViewOfTheMap(Map<Integer, String> map) {
		map.put(1, "A");
		map.put(2, "B");
		Set<Integer> keySet = map.keySet();
		assertTrue(keySet.remove(1));
		assertFalse(map.containsKey(1));
		assertEquals(1, map.size());
	}

	@ParameterizedTest
	@MethodSource("allImplementations")
	public void removeIfOnValuesWritesThroughToTheMap(Map<Integer, String> map) {
		for (int i = 0; i < 10; ++i) {
			map.put(i, i % 2 == 0 ? "even" : "odd");
		}
		assertTrue(map.values().removeIf("odd"::equals));
		assertEquals(5, map.size());
		assertFalse(map.containsValue("odd"));
		assertTrue(map.containsValue("even"));
	}

	@ParameterizedTest
	@MethodSource("allImplementations")
	public void entrySetSetValueReturnsPreviousValueAndWritesThrough(Map<Integer, String> map) {
		map.put(1, "A");
		Entry<Integer, String> entry = map.entrySet().iterator().next();
		assertEquals("A", entry.setValue("B"));
		assertEquals("B", map.get(1));
	}

	@ParameterizedTest
	@MethodSource("allImplementations")
	public void entrySetEqualsAnotherMapsEntrySet(Map<Integer, String> map) {
		map.put(1, "A");
		map.put(2, "B");
		Map<Integer, String> expected = Map.of(1, "A", 2, "B");
		assertEquals(expected.entrySet(), map.entrySet());
		assertEquals(map.entrySet(), expected.entrySet());
	}

	@ParameterizedTest
	@MethodSource("allImplementations")
	public void empty(Map<Integer, String> map) {
		assertTrue(map.isEmpty());
	}

	@ParameterizedTest
	@MethodSource("allImplementations")
	public void notEmptyAfterPut(Map<Integer, String> map) {
		map.put(1, "A");
		assertFalse(map.isEmpty());
	}

	@Test
	public void growsBeyondSmallInitialCapacity() {
		OpenAddressingMap<Integer, String> map = new OpenAddressingMap<>(3);
		for (int i = 0; i < 100; ++i) {
			map.put(i, "v" + i);
		}
		assertEquals(100, map.size());
		for (int i = 0; i < 100; ++i) {
			assertEquals("v" + i, map.get(i));
		}
	}

	@Test
	public void growsOnePutBeforeTheTableWouldFill() {
		// The table grows while one slot is still free rather than once it is full, so
		// a probe chain always terminates on an empty slot. Pinning the exact capacity
		// either side of the trigger is what keeps the growth policy from drifting: a
		// table that waited for the last slot, or grew a put early, still passes every
		// test that only checks the entries survive.
		OpenAddressingMap<Integer, String> map = new OpenAddressingMap<>(8);
		for (int i = 0; i < 7; ++i) {
			map.put(i, "v" + i);
		}
		assertEquals(7, map.size());
		assertEquals(8, map.capacity(), "the seventh entry still fits the original table");

		map.put(7, "v7");
		assertEquals(8, map.size());
		assertEquals(9, map.capacity(), "the eighth entry grows the table by one slot");
		for (int i = 0; i < 8; ++i) {
			assertEquals("v" + i, map.get(i));
		}
	}

	@Test
	public void churnOfDistinctKeysGrowsTheTableEvenWhileEmpty() {
		// A tombstone can only be revived by its own key, so churning distinct keys
		// leaves one behind on every cycle until no slot is empty. The table must then
		// grow to clear them, even though it holds nothing.
		OpenAddressingMap<Integer, String> map = new OpenAddressingMap<>(8);
		int initialCapacity = map.capacity();
		for (int key = 0; key < 30; ++key) {
			map.put(key, "v" + key);
			map.remove(key);
		}
		assertEquals(0, map.size());
		assertTrue(map.isEmpty());
		assertTrue(map.capacity() > initialCapacity,
				"tombstones from distinct keys must force the table to grow");

		map.put(999, "last");
		assertEquals("last", map.get(999), "the map still works once the tombstones are cleared");
		assertEquals(1, map.size());
	}

	@Test
	public void clearResizesToTheSizeTheMapHeld() {
		// clear() rebuilds the array at the size the map held rather than reusing the
		// grown one, so clearing a table that grew hands the memory back.
		OpenAddressingMap<Integer, String> map = new OpenAddressingMap<>(8);
		for (int i = 0; i < 5; ++i) {
			map.put(i, "v" + i);
		}
		assertEquals(8, map.capacity());

		map.clear();
		assertEquals(0, map.size());
		assertEquals(5, map.capacity(), "the cleared table shrinks to the size it held");
	}

	@Test
	public void clearOnAnEmptyMapShrinksToTheMinimumTable() {
		// An array of 2 would force prime == 1, so 3 is the floor a cleared empty map
		// lands on rather than 0.
		OpenAddressingMap<Integer, String> map = new OpenAddressingMap<>(8);
		map.clear();
		assertEquals(0, map.size());
		assertEquals(3, map.capacity());
	}

	@ParameterizedTest
	@MethodSource("allImplementations")
	public void equalsAndHashCodeAreDefinedByTheEntries(Map<Integer, String> map) {
		map.put(1, "A");
		map.put(2, "B");
		Map<Integer, String> expected = Map.of(1, "A", 2, "B");

		assertEquals(expected, map);
		assertEquals(map, expected);
		assertEquals(expected.hashCode(), map.hashCode());
	}

	@ParameterizedTest
	@MethodSource("allImplementations")
	public void equalsIgnoresRemovedEntries(Map<Integer, String> map) {
		// The tombstone a removal leaves behind is not an entry, so it must not make
		// the map differ from one that never held the key.
		map.put(1, "A");
		map.put(2, "B");
		map.remove(1);

		assertEquals(Map.of(2, "B"), map);
	}

	@ParameterizedTest
	@MethodSource("allImplementations")
	public void twoEmptyMapsAreEqual(Map<Integer, String> map) {
		assertEquals(Map.of(), map);
		assertEquals(map, Map.of());
		assertEquals(Map.of().hashCode(), map.hashCode());
	}

	@ParameterizedTest
	@MethodSource("allImplementations")
	public void mapsWithDifferentEntriesAreNotEqual(Map<Integer, String> map) {
		map.put(1, "A");

		assertNotEquals(map, Map.of(1, "B"));
		assertNotEquals(Map.of(1, "B"), map);
		assertNotEquals(map, Map.of(2, "A"));
		assertNotEquals(map, Map.of(1, "A", 2, "B"));
		assertNotEquals(map, "not a map");
	}

	@ParameterizedTest
	@MethodSource("allImplementations")
	public void toStringListsTheEntries(Map<Integer, String> map) {
		assertEquals("{}", map.toString());

		map.put(1, "A");
		assertEquals("{1=A}", map.toString());
	}

	@ParameterizedTest
	@MethodSource("allImplementations")
	public void anEntryPrintsAsKeyEqualsValue(Map<Integer, String> map) {
		map.put(1, "A");

		assertEquals("1=A", map.entrySet().iterator().next().toString());
	}

	@ParameterizedTest
	@MethodSource("allImplementations")
	public void iteratorFailsFastWhenAKeyIsAdded(Map<Integer, String> map) {
		map.put(1, "A");
		map.put(2, "B");
		Iterator<Integer> iterator = map.keySet().iterator();
		iterator.next();

		map.put(3, "C");

		assertThrows(ConcurrentModificationException.class, iterator::next);
	}

	@ParameterizedTest
	@MethodSource("allImplementations")
	public void iteratorFailsFastWhenAKeyIsRemoved(Map<Integer, String> map) {
		map.put(1, "A");
		map.put(2, "B");
		Iterator<String> iterator = map.values().iterator();
		iterator.next();

		map.remove(1);

		assertThrows(ConcurrentModificationException.class, iterator::next);
	}

	@ParameterizedTest
	@MethodSource("allImplementations")
	public void iteratorFailsFastAfterClear(Map<Integer, String> map) {
		map.put(1, "A");
		map.put(2, "B");
		Iterator<Entry<Integer, String>> iterator = map.entrySet().iterator();
		iterator.next();

		map.clear();

		assertThrows(ConcurrentModificationException.class, iterator::next);
	}

	@ParameterizedTest
	@MethodSource("allImplementations")
	public void iteratorRemoveFailsFastWhenTheMapChangedUnderneathIt(Map<Integer, String> map) {
		map.put(1, "A");
		map.put(2, "B");
		Iterator<Integer> iterator = map.keySet().iterator();
		iterator.next();

		map.put(3, "C");

		assertThrows(ConcurrentModificationException.class, iterator::remove);
	}

	@ParameterizedTest
	@MethodSource("allImplementations")
	public void iteratorOwnRemoveDoesNotFailFast(Map<Integer, String> map) {
		for (int i = 0; i < 10; ++i) {
			map.put(i, "v" + i);
		}

		Iterator<Integer> iterator = map.keySet().iterator();
		while (iterator.hasNext()) {
			if (iterator.next() % 2 == 0) {
				iterator.remove();
			}
		}

		assertEquals(5, map.size());
		assertFalse(map.containsKey(0));
		assertTrue(map.containsKey(1));
	}

	@Test
	public void aGrowingPutFailsTheIteratorEvenWhenItOnlyOverwrites() {
		// The rehash replaces the array the iterator walks, so it is a structural
		// modification in its own right even though no entry was added.
		OpenAddressingMap<Integer, String> map = new OpenAddressingMap<>(8);
		for (int i = 0; i < 7; ++i) {
			map.put(i, "v" + i);
		}
		Iterator<Integer> iterator = map.keySet().iterator();
		iterator.next();

		map.put(0, "overwritten"); // the table is one put away from growing

		assertEquals(7, map.size());
		assertThrows(ConcurrentModificationException.class, iterator::next);
	}
}
