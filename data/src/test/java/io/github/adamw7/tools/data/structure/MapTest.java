package io.github.adamw7.tools.data.structure;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

public class MapTest {
	static Stream<Arguments> happyPathImplementations() {
		return Stream.of(Arguments.of(new HashMap<Integer, String>()),
				Arguments.of(new OpenAddressingMap<Integer, String>()));
	}

	@ParameterizedTest
	@MethodSource("happyPathImplementations")
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
	@MethodSource("happyPathImplementations")
	public void overwrite(Map<Integer, String> map) {
		map.put(1, "A");
		assertTrue(map.size() == 1);
		assertTrue(map.get(1).equals("A"));
		map.put(1, "B");
		assertTrue(map.size() == 1);
		assertTrue(map.get(1).equals("B"));
	}

	@ParameterizedTest
	@MethodSource("happyPathImplementations")
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
	@MethodSource("happyPathImplementations")
	public void remove(Map<Integer, String> map) {
		map.put(1, "A");
		map.put(2, "B");
		map.remove(1);
		assertTrue(map.size() == 1);
		assertTrue(map.get(1) == null);
		assertTrue(map.get(2).equals("B"));
	}

	@ParameterizedTest
	@MethodSource("happyPathImplementations")
	public void containsKey(Map<Integer, String> map) {
		map.put(1, "A");
		map.put(2, "B");
		assertTrue(map.containsKey(1));
		assertTrue(map.containsKey(2));
		assertFalse(map.containsKey(3));

	}

	@ParameterizedTest
	@MethodSource("happyPathImplementations")
	public void containsValue(Map<Integer, String> map) {
		map.put(1, "A");
		map.put(2, "B");
		assertTrue(map.containsValue("A"));
		assertTrue(map.containsValue("B"));
		assertFalse(map.containsValue("C"));

	}

	@ParameterizedTest
	@MethodSource("happyPathImplementations")
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
}
