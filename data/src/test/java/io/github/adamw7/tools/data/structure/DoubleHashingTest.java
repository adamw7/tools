package io.github.adamw7.tools.data.structure;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import io.github.adamw7.tools.data.structure.internal.DoubleHashing;

public class DoubleHashingTest {

	@Test
	public void tableSizeKeepsSizesAtOrAboveFloor() {
		assertEquals(3, DoubleHashing.tableSize(1));
		assertEquals(3, DoubleHashing.tableSize(2));
		assertEquals(3, DoubleHashing.tableSize(3));
		assertEquals(64, DoubleHashing.tableSize(64));
	}

	@Test
	public void tableSizeRejectsZero() {
		assertWrongSize(0, () -> DoubleHashing.tableSize(0));
	}

	@Test
	public void tableSizeRejectsNegative() {
		assertWrongSize(-5, () -> DoubleHashing.tableSize(-5));
	}

	private void assertWrongSize(int size, Executable executable) {
		IllegalArgumentException thrown = assertThrows(IllegalArgumentException.class, executable);
		assertEquals("Wrong size: " + size, thrown.getMessage());
	}

	@Test
	public void grownSizeAppliesTheMultiplier() {
		assertEquals(76, DoubleHashing.grownSize(64)); // 64 * 1.2 = 76.8, truncated
		assertEquals(12, DoubleHashing.grownSize(10));
	}

	@Test
	public void grownSizeAlwaysGrowsForSmallTables() {
		// (int) (3 * 1.2) == 3 and (int) (4 * 1.2) == 4, so the multiplier alone
		// would never grow the smallest tables and put() would recurse forever.
		assertEquals(4, DoubleHashing.grownSize(3));
		assertEquals(5, DoubleHashing.grownSize(4));
		assertTrue(DoubleHashing.grownSize(3) > 3);
		assertTrue(DoubleHashing.grownSize(4) > 4);
	}

	@Test
	public void probeFollowsTheDoubleHashingSequence() {
		// h1 = 3 - (5 % 3) = 1, h2 = 1 + (5 % 6) = 6
		assertEquals(1, DoubleHashing.probe(5, 3, 7, 0)); // (1 + 0*6) % 7
		assertEquals(0, DoubleHashing.probe(5, 3, 7, 1)); // (1 + 1*6) % 7
		assertEquals(6, DoubleHashing.probe(5, 3, 7, 2)); // (1 + 2*6) % 7
	}

	@Test
	public void probeIsDeterministic() {
		assertEquals(DoubleHashing.probe(42, 5, 11, 3), DoubleHashing.probe(42, 5, 11, 3));
	}

	@Test
	public void probeStaysWithinTheTableForAnyHashCode() {
		int length = 17;
		int prime = 13;
		for (int hashCode : new int[] { 0, 1, -1, 100, -100, Integer.MAX_VALUE, Integer.MIN_VALUE }) {
			assertProbesAreValidIndices(hashCode, prime, length);
		}
	}

	private void assertProbesAreValidIndices(int hashCode, int prime, int length) {
		for (int iteration = 0; iteration < length; iteration++) {
			int index = DoubleHashing.probe(hashCode, prime, length, iteration);
			assertTrue(index >= 0 && index < length,
					"index " + index + " out of range for hashCode " + hashCode);
		}
	}

	@Test
	public void probeVisitsEverySlotForMinValueHashCode() {
		// Integer.MIN_VALUE is the one hashCode whose Math.abs stays negative. A prior
		// step derived from Math.abs(hashCode) went non-positive for it, folding the
		// sequence back onto a subset of the slots (12 of 13 here). On a prime-sized
		// table the sequence must be a full permutation of every slot.
		int length = 13;
		int prime = 11;
		assertProbesAllDistinct(Integer.MIN_VALUE, prime, length);
	}

	private void assertProbesAllDistinct(int hashCode, int prime, int length) {
		Set<Integer> visited = new HashSet<>();
		for (int iteration = 0; iteration < length; iteration++) {
			visited.add(DoubleHashing.probe(hashCode, prime, length, iteration));
		}
		assertEquals(length, visited.size(),
				"probe should visit every slot exactly once for hashCode " + hashCode);
	}
}
