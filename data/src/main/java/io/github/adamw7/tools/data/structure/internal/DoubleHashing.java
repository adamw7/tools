package io.github.adamw7.tools.data.structure.internal;

/**
 * The double-hashing probe arithmetic shared by the open-addressing maps.
 * Operating purely on a precomputed {@code hashCode} keeps it independent of the
 * key's type, so both the object-keyed {@code OpenAddressingMap} and the
 * primitive {@code int}-keyed {@code IntKeyOpenAddressingMap} reuse the same
 * sequence and growth policy rather than re-deriving them.
 */
public final class DoubleHashing {

	/** Growth factor applied to the backing array on each resize. */
	public static final double MULTIPLIER = 1.2;

	/** Backing-array size for a map created without an explicit size. */
	public static final int DEFAULT_SIZE = 64;

	private DoubleHashing() {
	}

	/**
	 * The legal backing-array size for a requested capacity. An array of size 2
	 * would force {@code prime == 1}, so 3 is the floor.
	 *
	 * @throws IllegalArgumentException when {@code requestedSize} is not positive
	 */
	public static int tableSize(int requestedSize) {
		if (requestedSize <= 0) {
			throw new IllegalArgumentException("Wrong size: " + requestedSize);
		}
		return Math.max(requestedSize, 3);
	}

	/** The backing-array size after one growth step. */
	public static int grownSize(int currentLength) {
		return (int) (currentLength * MULTIPLIER);
	}

	/** The slot index probed on the given {@code iteration} of the sequence. */
	public static int probe(int hashCode, int prime, int length, int iteration) {
		int h1 = prime - (hashCode % prime);
		int h2 = 1 + (Math.abs(hashCode) % (length - 1));
		return Math.abs((h1 + (iteration * h2)) % length);
	}
}
