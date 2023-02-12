package io.github.adamw7.tools.data.structure;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

public class PrimesTest {

	@Test
	public void happyPath() {
		assertEquals(true, Primes.isPrime(5));
		assertEquals(false, Primes.isPrime(10));
		assertEquals(7, Primes.findMaxSmallerThan(10));
		assertEquals(true, Primes.isPrime(7907));
		assertEquals(false, Primes.isPrime(7920));
	}
	
	@Test
	public void negativeTooLowNumber() {
		IllegalArgumentException thrown = assertThrows(IllegalArgumentException.class, () -> {
			Primes.findMaxSmallerThan(2);
		}, "Expected findMaxSmallerThan method to throw, but it didn't");

		assertEquals(thrown.getMessage(), "No primes smaller than 2");
	}
}
