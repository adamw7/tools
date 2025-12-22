package io.github.adamw7.tools.data.structure;

import static io.github.adamw7.tools.data.Utils.named;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.params.provider.Arguments.of;

import java.util.Collection;
import java.util.HashMap;
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
	public void values(Map<Integer, String> map) {
		int size = 50;
		for (int i = 0; i < size; ++i) {
			map.put(i, String.valueOf(i));
		}

		Collection<String> values = map.values();
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
	public void nullKey() {
		IllegalArgumentException thrown = assertThrows(IllegalArgumentException.class, () -> new OpenAddressingMap<>(2).put(null, null), "Expected put method to throw, but it didn't");

		assertEquals("Key is null",thrown.getMessage());
	}
	
	@ParameterizedTest
	@MethodSource("allImplementations")
	public void empty(Map<Integer, String> map) {
		assertTrue(map.isEmpty());
	}
}
