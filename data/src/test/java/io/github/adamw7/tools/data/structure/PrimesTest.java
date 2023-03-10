package io.github.adamw7.tools.data.structure;

import org.junit.jupiter.api.Test;

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
	
	@Test
	public void negativeTooLowNumber() {
		IllegalArgumentException thrown = assertThrows(IllegalArgumentException.class, () -> Primes.findMaxSmallerThan(2), "Expected findMaxSmallerThan method to throw, but it didn't");

		assertEquals(thrown.getMessage(), "No primes smaller than 2");
	}
}
