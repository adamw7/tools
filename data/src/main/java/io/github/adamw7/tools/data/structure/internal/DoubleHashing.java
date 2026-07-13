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

	/**
	 * The backing-array size after one growth step. The multiplier alone does not
	 * guarantee progress for small arrays &mdash; {@code (int) (3 * 1.2) == 3} and
	 * {@code (int) (4 * 1.2) == 4} &mdash; so growth is floored at
	 * {@code currentLength + 1} to ensure the table always gets strictly larger.
	 * Without this the open-addressing maps recurse forever in {@code put} once a
	 * small table fills up.
	 */
	public static int grownSize(int currentLength) {
		return Math.max((int) (currentLength * MULTIPLIER), currentLength + 1);
	}

	/**
	 * The slot index probed on the given {@code iteration} of the sequence.
	 *
	 * <p>Equivalent to {@code sequence(hashCode, prime, length).slot(iteration)};
	 * kept as a convenience for callers that need a single, isolated index. Callers
	 * that walk a whole probe chain should build one {@link Probe} and reuse it, so
	 * the iteration-independent components are derived only once.
	 */
	public static int probe(int hashCode, int prime, int length, int iteration) {
		return sequence(hashCode, prime, length).slot(iteration);
	}

	/**
	 * A reusable probe sequence for a single key. The two components that do not
	 * depend on the iteration &mdash; the starting offset {@code h1} and the step
	 * {@code h2} &mdash; are derived once here rather than on every probe, so a
	 * lookup or insert that walks several slots pays for the key's {@code hashCode}
	 * and the modulo arithmetic exactly once instead of once per probe.
	 */
	public static Probe sequence(int hashCode, int prime, int length) {
		return new Probe(hashCode, prime, length);
	}

	/**
	 * The iteration-independent state of a double-hashing probe sequence for one
	 * key. Immutable and cheap to allocate; it does not escape the lookup or insert
	 * that builds it, so the JIT can keep it on the stack.
	 */
	public static final class Probe {

		private final int h1;
		private final int h2;
		private final int length;

		/**
		 * The step {@code h2} is derived from {@code Math.abs(hashCode % (length - 1))}
		 * rather than {@code Math.abs(hashCode) % (length - 1)}: the two agree for every
		 * {@code hashCode} except {@link Integer#MIN_VALUE}, whose {@code Math.abs} stays
		 * negative and would otherwise yield a non-positive step that folds the probe
		 * sequence back onto a handful of slots.
		 */
		private Probe(int hashCode, int prime, int length) {
			this.h1 = prime - (hashCode % prime);
			this.h2 = 1 + Math.abs(hashCode % (length - 1));
			this.length = length;
		}

		/** The slot index probed on the given {@code iteration} of the sequence. */
		public int slot(int iteration) {
			return Math.abs((h1 + (iteration * h2)) % length);
		}
	}
}
