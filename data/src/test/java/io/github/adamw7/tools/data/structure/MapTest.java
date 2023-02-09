package io.github.adamw7.tools.data.structure;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashMap;
import java.util.Map;
import java.util.stream.Stream;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

public class MapTest {
	static Stream<Arguments> happyPathImplementations() {
		return Stream.of(Arguments.of(new HashMap<Integer, String>()),
				Arguments.of(new OpenAdressingMap<Integer, String>()));
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
}
