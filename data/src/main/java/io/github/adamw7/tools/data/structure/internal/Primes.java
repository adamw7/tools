package io.github.adamw7.tools.data.structure.internal;

public class Primes {

	private Primes() {}
	
	public static boolean isPrime(int n) {
		if (n < 2) {
			return false;
		}

		for (int i = 2; i <= n / i; i++) {
			if (n % i == 0) {
				return false;
			}
		}

		return true;
	}

	/**
	 * The smallest prime at or above {@code min}. The open-addressing tables are sized
	 * with it, so that every probe step is coprime with the table length and a probe
	 * sequence visits every slot.
	 *
	 * @throws IllegalArgumentException when no {@code int} prime lies at or above {@code min}
	 */
	public static int findMinAtLeast(int min) {
		for (int i = Math.max(min, 2); i > 0; i++) {
			if (isPrime(i)) {
				return i;
			}
		}
		throw new IllegalArgumentException("No primes at or above " + min);
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
