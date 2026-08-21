package io.github.adamw7.tools.data.structure;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import io.github.adamw7.tools.data.structure.internal.Primes;

import static org.junit.jupiter.api.Assertions.*;

public class PrimesTest {

	@Test
	public void happyPath() {
		assertTrue(Primes.isPrime(5));
		assertFalse(Primes.isPrime(10));
		assertEquals(7, Primes.findMaxSmallerThan(10));
		assertTrue(Primes.isPrime(7907));
		assertFalse(Primes.isPrime(7920));
	}

	@ParameterizedTest
	@ValueSource(ints = {2, 3, 5, 7, 13, 7907, 46337, 2147483647})
	public void primesAreRecognised(int n) {
		assertTrue(Primes.isPrime(n), n + " is prime");
	}

	@ParameterizedTest
	@ValueSource(ints = {4, 9, 25, 7920, 2147483645, 2147483646})
	public void compositesAreRejected(int n) {
		assertFalse(Primes.isPrime(n), n + " is composite");
	}

	@ParameterizedTest
	@ValueSource(ints = {1, 0, -1, -7, Integer.MIN_VALUE})
	public void numbersBelowTwoAreNotPrime(int n) {
		assertFalse(Primes.isPrime(n), n + " is smaller than 2, so it is not prime");
	}

	@Test
	public void findsTheLargestPrimeBelowTheBound() {
		assertEquals(2, Primes.findMaxSmallerThan(3));
		assertEquals(3, Primes.findMaxSmallerThan(4));
		assertEquals(7907, Primes.findMaxSmallerThan(7908));
	}

	@Test
	public void negativeTooLowNumber() {
		IllegalArgumentException thrown = assertThrows(IllegalArgumentException.class, () -> Primes.findMaxSmallerThan(2), "Expected findMaxSmallerThan method to throw, but it didn't");

		assertEquals("No primes smaller than 2", thrown.getMessage());
	}
}
