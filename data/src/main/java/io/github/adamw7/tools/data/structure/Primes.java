package io.github.adamw7.tools.data.structure;

public class Primes {

	private Primes() {}
	
	public static boolean isPrime(int n) {
		for (int i = 2; i * i <= n; i++)
			if (n % i == 0) {
				return false;
			}

		return true;
	}

	public static int findMaxSmallerThan(int max) {
		for (int i = max - 1; i >= 2; i--) {
			if (isPrime(i)) {
				return i;
			}
		}
		throw new IllegalArgumentException("No primes smaller than " + max);
	}
}
