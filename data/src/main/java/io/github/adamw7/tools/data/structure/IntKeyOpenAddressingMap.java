package io.github.adamw7.tools.data.structure;

import java.util.ArrayList;
import java.util.Collection;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import io.github.adamw7.tools.data.structure.internal.DoubleHashing;
import io.github.adamw7.tools.data.structure.internal.Primes;

/**
 * A primitive {@code int}-keyed sibling of {@link OpenAddressingMap}: the same
 * double-hashing open-addressing strategy, but keys live in an {@code int[]} so
 * lookups and inserts never box. That makes it an allocation-light choice for
 * large, integer-keyed maps where the autoboxing of a {@code Map<Integer, V>}
 * would dominate.
 *
 * <p>It deliberately does <em>not</em> implement {@link java.util.Map}, which is
 * defined in terms of {@code Object} keys and would reintroduce the boxing this
 * class exists to avoid; the API mirrors the relevant map operations with
 * primitive keys instead. As in {@link OpenAddressingMap}, {@code null} values are
 * stored faithfully and reported by {@link #containsKey(int)}; only
 * {@link #get(int)} cannot tell a stored {@code null} from an absent key. Not
 * thread-safe.
 */
public class IntKeyOpenAddressingMap<V> {

	static final int DEFAULT_SIZE = DoubleHashing.DEFAULT_SIZE;

	private static final byte EMPTY = 0;
	private static final byte LIVE = 1;
	private static final byte TOMBSTONE = 2;

	private int prime;
	private int[] keys;
	private V[] values;
	private byte[] state;
	private int size;

	public IntKeyOpenAddressingMap(int size) {
		initArrays(size);
	}

	public IntKeyOpenAddressingMap() {
		this(DEFAULT_SIZE);
	}

	@SuppressWarnings("unchecked")
	private void initArrays(int size) {
		int newSize = DoubleHashing.tableSize(size);
		keys = new int[newSize];
		values = (V[]) new Object[newSize];
		state = new byte[newSize];
		prime = Primes.findMaxSmallerThan(newSize);
	}

	public int size() {
		return size;
	}

	public boolean isEmpty() {
		return size == 0;
	}

	/** The current backing-array length; exposed for tests that assert growth behaviour. */
	int capacity() {
		return keys.length;
	}

	public boolean containsKey(int key) {
		return indexOf(key) >= 0;
	}

	public boolean containsValue(Object value) {
		return values().contains(value);
	}

	public V get(int key) {
		int index = indexOf(key);
		return index < 0 ? null : values[index];
	}

	public V getOrDefault(int key, V defaultValue) {
		int index = indexOf(key);
		return index < 0 ? defaultValue : values[index];
	}

	/**
	 * Probes the double-hashing sequence for {@code key} and returns the index of
	 * its live slot, or {@code -1} when the key is absent. An {@link #EMPTY} slot
	 * ends the search; {@link #TOMBSTONE} slots are skipped because live entries
	 * may sit past them in the probe chain.
	 */
	private int indexOf(int key) {
		DoubleHashing.Probe probe = probe(key);
		for (int i = 0; i < keys.length; ++i) {
			int index = probe.slot(i);
			if (state[index] == EMPTY) {
				return -1;
			}
			if (state[index] == LIVE && keys[index] == key) {
				return index;
			}
		}
		return -1;
	}

	private DoubleHashing.Probe probe(int key) {
		return DoubleHashing.sequence(Integer.hashCode(key), prime, keys.length);
	}

	/**
	 * Probes the double-hashing sequence for {@code key} and stores {@code value}.
	 * An {@link #EMPTY} slot ends the search and receives a fresh entry. A slot
	 * already holding {@code key} is reused in place: a {@link #LIVE} one is
	 * overwritten, a {@link #TOMBSTONE} one is revived. Reviving the key's own
	 * tombstone rather than probing past it keeps a remove/re-add cycle of the same
	 * key from leaking a tombstone on every pass, which would otherwise fill the
	 * probe chain and force needless resizes. Tombstones for a <em>different</em>
	 * key are skipped, because the sought key may still sit further along the chain.
	 */
	public V put(int key, V value) {
		checkIfResizeNeeded();
		DoubleHashing.Probe probe = probe(key);
		for (int i = 0; i < keys.length; ++i) {
			int index = probe.slot(i);
			if (state[index] == EMPTY) {
				return insert(index, key, value);
			} else if (keys[index] == key) {
				return state[index] == TOMBSTONE ? insert(index, key, value) : overwrite(index, value);
			} // slots holding a different key, live or tombstoned, are skipped
		}
		resize();
		return put(key, value);
	}

	private V insert(int index, int key, V value) {
		keys[index] = key;
		values[index] = value;
		state[index] = LIVE;
		size++;
		return null;
	}

	private V overwrite(int index, V value) {
		V previous = values[index];
		values[index] = value;
		return previous;
	}

	private void checkIfResizeNeeded() {
		if (size + 1 >= keys.length) {
			resize();
		}
	}

	private void resize() {
		int[] oldKeys = keys;
		V[] oldValues = values;
		byte[] oldState = state;
		initArrays(newSize());
		size = 0;
		for (int i = 0; i < oldState.length; ++i) {
			if (oldState[i] == LIVE) {
				put(oldKeys[i], oldValues[i]);
			}
		}
	}

	public V remove(int key) {
		int index = indexOf(key);
		if (index < 0) {
			return null;
		}
		V previous = values[index];
		state[index] = TOMBSTONE;
		values[index] = null;
		size--;
		return previous;
	}

	public void clear() {
		initArrays(keys.length);
		size = 0;
	}

	private int newSize() {
		return DoubleHashing.grownSize(keys.length);
	}

	public int[] keys() {
		return liveSlots().map(slot -> keys[slot]).toArray();
	}

	public Collection<V> values() {
		return liveSlots().mapToObj(slot -> values[slot])
				.collect(Collectors.toCollection(() -> new ArrayList<>(size)));
	}

	/**
	 * The slots holding a live entry, in table order — how both views of the map's
	 * contents are walked, so the {@link #LIVE} test is written once.
	 */
	private IntStream liveSlots() {
		return IntStream.range(0, state.length).filter(slot -> state[slot] == LIVE);
	}
}
