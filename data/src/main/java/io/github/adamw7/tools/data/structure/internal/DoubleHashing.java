package io.github.adamw7.tools.data.structure.internal;

/**
 * The double-hashing probe arithmetic shared by the open-addressing maps.
 * Operating purely on a precomputed {@code hashCode} keeps it independent of the
 * key's type, so both the object-keyed {@code OpenAddressingMap} and the
 * primitive {@code int}-keyed {@code IntKeyOpenAddressingMap} reuse the same
 * sequence and growth policy rather than re-deriving them.
 */
public final class DoubleHashing {

	/**
	 * Growth factor applied to the backing array on each resize. Doubling keeps the
	 * cost of the rehashes amortised to a constant per insert.
	 */
	public static final int GROWTH_FACTOR = 2;

	/**
	 * The share of the backing array that live entries and tombstones together may
	 * occupy. Past it the expected probe chain lengthens sharply, so the table is
	 * rehashed before an insert would cross it.
	 */
	public static final double MAX_LOAD_FACTOR = 0.75;

	/** Backing-array size requested for a map created without an explicit size. */
	public static final int DEFAULT_SIZE = 64;

	private DoubleHashing() {
	}

	/**
	 * The backing-array size for a requested capacity: the smallest prime at or above
	 * it, and at least 3, since an array of size 2 would force {@code prime == 1}. A
	 * prime length is coprime with every probe step, so a probe sequence visits every
	 * slot before it repeats one.
	 *
	 * @throws IllegalArgumentException when {@code requestedSize} is not positive
	 */
	public static int tableSize(int requestedSize) {
		if (requestedSize <= 0) {
			throw new IllegalArgumentException("Wrong size: " + requestedSize);
		}
		return Primes.findMinAtLeast(Math.max(requestedSize, 3));
	}

	/** The backing-array size after one growth step: {@link #GROWTH_FACTOR} times larger, rounded up to a prime. */
	public static int grownSize(int currentLength) {
		return tableSize((int) Math.min((long) currentLength * GROWTH_FACTOR, Integer.MAX_VALUE - 8));
	}

	/**
	 * Whether {@code occupiedSlots} &mdash; live entries plus tombstones &mdash; would
	 * load a table of {@code length} slots past {@link #MAX_LOAD_FACTOR}.
	 */
	public static boolean overloaded(int occupiedSlots, int length) {
		return occupiedSlots > length * MAX_LOAD_FACTOR;
	}

	/**
	 * The backing-array size to rehash an overloaded table into. When the live entries
	 * alone fill no more than half of what the load factor allows, it is tombstones
	 * crowding the table, and rehashing at the current size clears them without
	 * growing; otherwise the table grows. Without that, churning distinct keys through
	 * a map of constant size would double its table on every rehash.
	 */
	public static int rehashedSize(int currentLength, int liveEntries) {
		return overloaded(2 * (liveEntries + 1), currentLength) ? grownSize(currentLength) : currentLength;
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

		/**
		 * The slot index probed on the given {@code iteration} of the sequence. The
		 * arithmetic is done in {@code long}: {@code iteration * h2} overflows an
		 * {@code int} on any table past about 46,000 slots, which would scramble the
		 * sequence rather than walk it.
		 */
		public int slot(int iteration) {
			return (int) Math.abs((h1 + ((long) iteration * h2)) % length);
		}
	}
}
