package io.github.adamw7.tools.data.structure;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.params.provider.Arguments.of;

import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

public class OpenAddressingSetTest {

	static Stream<Arguments> implementations() {
		return Stream.of(of(new HashSet<Integer>()), of(new OpenAddressingSet<Integer>()),
				of(new OpenAddressingSet<Integer>(16)));
	}

	@ParameterizedTest
	@MethodSource("implementations")
	public void addReturnsTrueForNewElement(Set<Integer> set) {
		assertTrue(set.add(1));
		assertEquals(1, set.size());
	}

	@ParameterizedTest
	@MethodSource("implementations")
	public void addReturnsFalseForDuplicate(Set<Integer> set) {
		set.add(1);
		assertFalse(set.add(1));
		assertEquals(1, set.size());
	}

	@ParameterizedTest
	@MethodSource("implementations")
	public void contains(Set<Integer> set) {
		set.add(1);
		set.add(2);
		assertTrue(set.contains(1));
		assertTrue(set.contains(2));
		assertFalse(set.contains(3));
	}

	@ParameterizedTest
	@MethodSource("implementations")
	public void removeReturnsTrueWhenPresent(Set<Integer> set) {
		set.add(1);
		assertTrue(set.remove(1));
		assertFalse(set.contains(1));
		assertEquals(0, set.size());
	}

	@ParameterizedTest
	@MethodSource("implementations")
	public void removeReturnsFalseWhenAbsent(Set<Integer> set) {
		set.add(1);
		assertFalse(set.remove(999));
		assertEquals(1, set.size());
	}

	@ParameterizedTest
	@MethodSource("implementations")
	public void emptyThenNotEmpty(Set<Integer> set) {
		assertTrue(set.isEmpty());
		set.add(1);
		assertFalse(set.isEmpty());
	}

	@ParameterizedTest
	@MethodSource("implementations")
	public void iteratorVisitsEveryElement(Set<Integer> set) {
		int count = 200; // forces several resizes
		for (int i = 0; i < count; ++i) {
			set.add(i);
		}
		Set<Integer> seen = new HashSet<>();
		for (Integer element : set) {
			seen.add(element);
		}
		assertEquals(count, set.size());
		assertEquals(count, seen.size());
		for (int i = 0; i < count; ++i) {
			assertTrue(seen.contains(i), i + " was not iterated");
		}
	}

	@ParameterizedTest
	@MethodSource("implementations")
	public void clearOnEmptySet(Set<Integer> set) {
		set.clear();
		assertEquals(0, set.size());
		assertTrue(set.isEmpty());
	}

	@ParameterizedTest
	@MethodSource("implementations")
	public void clearAfterInserts(Set<Integer> set) {
		for (int i = 0; i < 100; ++i) {
			set.add(i);
		}
		set.clear();
		assertEquals(0, set.size());
		assertFalse(set.contains(0));
	}

	@ParameterizedTest
	@MethodSource("implementations")
	public void addAllAndContainsAll(Set<Integer> set) {
		Set<Integer> source = new HashSet<>();
		for (int i = 0; i < 50; ++i) {
			source.add(i);
		}
		assertTrue(set.addAll(source));
		assertTrue(set.containsAll(source));
		assertEquals(50, set.size());
	}

	@ParameterizedTest
	@MethodSource("implementations")
	public void removeAllWithLargerCollectionUsesTheIterator(Set<Integer> set) {
		set.add(1);
		set.add(2);
		set.add(3);
		// The collection is larger than the set, so AbstractSet.removeAll walks the
		// set's own iterator and relies on its remove() writing through.
		assertTrue(set.removeAll(List.of(0, 1, 2, 8, 9)));
		assertEquals(Set.of(3), set);
	}

	@ParameterizedTest
	@MethodSource("implementations")
	public void retainAllKeepsOnlyGivenElements(Set<Integer> set) {
		for (int i = 0; i < 10; ++i) {
			set.add(i);
		}
		assertTrue(set.retainAll(List.of(1, 3, 5)));
		assertEquals(Set.of(1, 3, 5), set);
	}

	@ParameterizedTest
	@MethodSource("implementations")
	public void removeIfRemovesMatchingElements(Set<Integer> set) {
		for (int i = 0; i < 10; ++i) {
			set.add(i);
		}
		assertTrue(set.removeIf(element -> element % 2 == 0));
		assertEquals(Set.of(1, 3, 5, 7, 9), set);
	}

	@ParameterizedTest
	@MethodSource("implementations")
	public void iteratorRemoveWritesThroughToTheSet(Set<Integer> set) {
		set.add(1);
		set.add(2);
		Iterator<Integer> iterator = set.iterator();
		iterator.next();
		iterator.remove();
		assertEquals(1, set.size());
	}

	@Test
	public void conflictingHashesAreDistinctElements() {
		Set<ConflictingKey> set = new OpenAddressingSet<>();
		ConflictingKey first = new ConflictingKey(1, "1");
		ConflictingKey second = new ConflictingKey(2, "2");
		set.add(first);
		set.add(second);
		assertEquals(2, set.size());
		assertTrue(set.contains(first));
		assertTrue(set.contains(second));
	}

	@Test
	public void nullElementIsRejected() {
		assertThrows(IllegalArgumentException.class, () -> new OpenAddressingSet<Integer>().add(null));
	}
}
