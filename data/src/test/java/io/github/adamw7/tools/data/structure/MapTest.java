package io.github.adamw7.tools.data.structure;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import static io.github.adamw7.tools.data.Utils.named;
import static org.junit.jupiter.params.provider.Arguments.of;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.stream.Stream;

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
	
	@ParameterizedTest
	@MethodSource("allImplementations")
	public void happyPath(Map<Integer, String> map) {
		map.put(1, "A");
		assertTrue(map.size() == 1);
		assertTrue(map.get(1).equals("A"));
		map.put(2, "B");
		assertTrue(map.size() == 2);
		assertTrue(map.get(1).equals("A"));
		assertTrue(map.get(2).equals("B"));
	}

	@ParameterizedTest
	@MethodSource("allImplementations")
	public void overwrite(Map<Integer, String> map) {
		map.put(1, "A");
		assertTrue(map.size() == 1);
		assertTrue(map.get(1).equals("A"));
		map.put(1, "B");
		assertTrue(map.size() == 1);
		assertTrue(map.get(1).equals("B"));
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
		assertTrue(map.size() == 1);
		assertTrue(map.get(1) == null);
		assertTrue(map.get(2).equals("B"));
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
			assertTrue(value != null);
			assertTrue(value.equals(String.valueOf(i)));
		}
		assertTrue(map.size() == size);
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
		
		assertTrue(map.size() == 0);
		for (int i = 0; i < size; ++i) {
			String value = map.get(i);
			assertTrue(value == null);
		}
	}
	
	@ParameterizedTest
	@MethodSource("allImplementations")
	public void entrySet(Map<Integer, String> map) {
		final int size = 50;
		map.putAll(sampleMap(size));
		Set<Entry<Integer, String>> entrySet = map.entrySet();
		assertTrue(entrySet.size() == size);
		for (Entry<Integer, String> entry : entrySet) {
			assertTrue(map.get(entry.getKey()).equals(entry.getValue()));
		}
	}
	
	@ParameterizedTest
	@MethodSource("allImplementations")
	public void resize(Map<Integer, String> map) {
		final int size = OpenAddressingMap.DEFAULT_SIZE * 2; // forcing resize
		map.putAll(sampleMap(size)); 
		assertEquals(size, map.size());
		checkValues(map, size);
	}
	
	@ParameterizedTest
	@MethodSource("happyPathImplementations")
	public void resizeVsRemovals(Map<Integer, String> map) {
		final int size = OpenAddressingMap.DEFAULT_SIZE * 2; // forcing resize
		int keyToRemove = -10000;
		String valueToRemove = "ItemToRemove";
		map.put(keyToRemove, valueToRemove);
		assertEquals(1, map.size());
		String removed = map.remove(keyToRemove);
		assertEquals(0, map.size());
		assertEquals(removed, valueToRemove);
		map.putAll(sampleMap(size)); 
		assertEquals(size, map.size());
		checkValues(map, size);
	}
	
	@ParameterizedTest
	@MethodSource("allImplementations")
	public void multipleResize(Map<Integer, String> map) {
		final int size = OpenAddressingMap.DEFAULT_SIZE * 4; // forcing resize
		map.putAll(sampleMap(size));
		assertEquals(size, map.size());
		checkValues(map, size);
	}

	private void checkValues(Map<Integer, String> map, final int size) {
		for (int i = 0; i < size; ++i) {
			String value = map.get(i);
			assertEquals(value, String.valueOf(i));
		}
	}
	
	@ParameterizedTest
	@MethodSource("happyPathImplementationsWithCustomSize")
	public void customNonPrimeSize(Map<Integer, String> map) {
		int maxSize = CUSTOM_SIZE * 4;
		map.putAll(sampleMap(maxSize));
		assertEquals(maxSize, map.size());
		checkValues(map, maxSize);
		map.remove(5);
		assertEquals(maxSize - 1, map.size());
	}
	
	
}
