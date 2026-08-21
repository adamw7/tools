package io.github.adamw7.tools.data.structure;

import java.util.AbstractCollection;
import java.util.AbstractMap;
import java.util.AbstractSet;
import java.util.Collection;
import java.util.ConcurrentModificationException;
import java.util.Iterator;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;

import io.github.adamw7.tools.data.structure.internal.DoubleHashing;
import io.github.adamw7.tools.data.structure.internal.Primes;
import io.github.adamw7.tools.data.structure.internal.Wrapper;

/**
 * A {@link Map} using open addressing with double hashing: entries live directly
 * in one array, a removal leaves a tombstone behind, and the table grows while a
 * slot is still free so every probe chain ends on an empty one.
 *
 * <p>It extends {@link AbstractMap}, so {@code equals}, {@code hashCode} and
 * {@code toString} are the ones {@link Map} specifies over {@link #entrySet()} —
 * a map holding the same entries as a {@link java.util.HashMap} compares equal
 * to it.
 *
 * <p>{@code null} values are stored faithfully; {@code null} <em>keys</em> are
 * not supported. Per the {@code Map} contract {@link #put} rejects one with a
 * {@link NullPointerException}, while {@link #containsKey}, {@link #get} and
 * {@link #remove} simply answer {@code false}/{@code null} for a key the map
 * cannot hold.
 *
 * <p>The iterators of all three views are <em>fail-fast</em>: a structural
 * modification made through anything but the iterator's own {@code remove()}
 * makes the next {@code next()} or {@code remove()} throw
 * {@link ConcurrentModificationException}. That is a best-effort check, so it
 * must not be relied on for correctness. Not thread-safe.
 */
public class OpenAddressingMap<K, V> extends AbstractMap<K, V> {

	static final int DEFAULT_SIZE = DoubleHashing.DEFAULT_SIZE;
	int prime;

	protected Wrapper<K, V>[] array;
	protected int size;

	/**
	 * Counts the structural modifications — an insertion, a removal, a rehash or a
	 * clear, but not the overwrite of an existing key — so the view iterators can
	 * tell that the table changed underneath them.
	 */
	private int modCount;

	public OpenAddressingMap(int size) {
		initArray(size);
	}

	@SuppressWarnings("unchecked")
	private void initArray(int size) {
		int newSize = DoubleHashing.tableSize(size);
		array = new Wrapper[newSize];
		prime = Primes.findMaxSmallerThan(newSize);
	}

	public OpenAddressingMap() {
		this(DEFAULT_SIZE);
	}

	@Override
	public int size() {
		return size;
	}

	@Override
	public boolean isEmpty() {
		return size() == 0;
	}

	/** The current backing-array length; exposed for tests that assert growth behaviour. */
	int capacity() {
		return array.length;
	}

	@Override
	public boolean containsKey(Object key) {
		return find(key) != null;
	}

	@Override
	public boolean containsValue(Object value) {
		return values().contains(value);
	}

	@Override
	public V get(Object key) {
		Wrapper<K, V> wrapper = find(key);
		return wrapper == null ? null : wrapper.getValue();
	}

	/**
	 * Probes the double-hashing sequence for {@code key} and returns its live
	 * wrapper, or {@code null} when the key is absent. An empty (never-used) slot
	 * ends the search: {@link #put} always fills the first such slot, so the key
	 * cannot lie beyond it. Tombstones ({@code removed}) are skipped rather than
	 * treated as terminal, because live entries may sit past them.
	 *
	 * <p>A {@code null} key never reaches the probe: the map cannot hold one, so
	 * it is absent by definition and the lookups built on this method answer
	 * {@code false}/{@code null} instead of throwing, as {@link Map} requires.
	 */
	private Wrapper<K, V> find(Object key) {
		if (key == null) {
			return null;
		}
		DoubleHashing.Probe probe = probe(key);
		for (int i = 0; i < array.length; ++i) {
			Wrapper<K, V> wrapper = array[probe.slot(i)];
			if (wrapper == null) {
				return null;
			}
			if (valid(wrapper) && wrapper.getKey().equals(key)) {
				return wrapper;
			}
		}
		return null;
	}

	private DoubleHashing.Probe probe(Object key) {
		return DoubleHashing.sequence(key.hashCode(), prime, array.length);
	}

	@Override
	public V put(K key, V value) {
		Objects.requireNonNull(key, "Key is null");
		checkIfResizeNeeded();
		DoubleHashing.Probe probe = probe(key);
		for (int i = 0; i < array.length; ++i) {
			int hash = probe.slot(i);
			Wrapper<K, V> wrapper = array[hash];
			if (wrapper == null) {
				return insert(hash, key, value);
			} else if (wrapper.getKey().equals(key)) {
				return wrapper.isRemoved() ? insert(hash, key, value) : overwrite(hash, key, value);
			} // removed entries with a different key are skipped
		}
		resize();
		return put(key, value);
	}

	private V insert(int hash, K key, V value) {
		array[hash] = new Wrapper<>(key, value);
		size++;
		modCount++;
		return null;
	}

	private V overwrite(int hash, K key, V value) {
		V previous = array[hash].getValue();
		array[hash] = new Wrapper<>(key, value);
		return previous;
	}

	private void checkIfResizeNeeded() {
		if (size + 1 >= array.length) {
			resize();
		}
	}

	/**
	 * Rehashes into a larger table. It counts as a structural modification in its
	 * own right, because it replaces the array an iterator is walking even when the
	 * put that triggered it only overwrote an existing key.
	 */
	private void resize() {
		modCount++;
		Wrapper<K, V>[] old = array;
		initArray(newSize());
		size = 0;
		for (Wrapper<K, V> wrapper : old) {
			if (valid(wrapper)) {
				put(wrapper.getKey(), wrapper.getValue());
			}
		}
	}

	@Override
	public V remove(Object key) {
		Wrapper<K, V> wrapper = find(key);
		if (wrapper == null) {
			return null;
		}
		wrapper.markRemoved();
		size--;
		modCount++;
		return wrapper.getValue();
	}

	@Override
	public void putAll(Map<? extends K, ? extends V> map) {
		map.forEach(this::put);
	}

	@Override
	public void clear() {
		initArray(size >= array.length ? newSize() : Math.max(size, 1));
		size = 0;
		modCount++;
	}

	private int newSize() {
		return DoubleHashing.grownSize(array.length);
	}

	@Override
	public Set<K> keySet() {
		return new KeySetView();
	}

	@Override
	public Collection<V> values() {
		return new ValuesView();
	}

	@Override
	public Set<Entry<K, V>> entrySet() {
		return new EntrySetView();
	}

	private boolean valid(Wrapper<K, V> wrapper) {
		return wrapper != null && !wrapper.isRemoved();
	}

	private final class KeySetView extends AbstractSet<K> {

		@Override
		public Iterator<K> iterator() {
			return new LiveEntryIterator<>(Wrapper::getKey);
		}

		@Override
		public int size() {
			return OpenAddressingMap.this.size();
		}

		@Override
		public boolean contains(Object key) {
			return containsKey(key);
		}
	}

	private final class ValuesView extends AbstractCollection<V> {

		@Override
		public Iterator<V> iterator() {
			return new LiveEntryIterator<>(Wrapper::getValue);
		}

		@Override
		public int size() {
			return OpenAddressingMap.this.size();
		}
	}

	private final class EntrySetView extends AbstractSet<Entry<K, V>> {

		@Override
		public Iterator<Entry<K, V>> iterator() {
			return new LiveEntryIterator<>(wrapper -> wrapper);
		}

		@Override
		public int size() {
			return OpenAddressingMap.this.size();
		}
	}

	/**
	 * Walks the live entries of the backing array, mapping each wrapper to the
	 * view's element type. {@code remove()} retires the entry returned last, so
	 * the views are writable and the bulk operations inherited from
	 * {@code AbstractCollection} (removeAll, retainAll, removeIf) really mutate
	 * the map instead of a throwaway copy. Any other structural modification made
	 * while the walk is in progress is refused with a
	 * {@link ConcurrentModificationException} rather than silently yielding
	 * entries from a stale table.
	 */
	private final class LiveEntryIterator<T> implements Iterator<T> {

		private final Function<Wrapper<K, V>, T> mapper;
		private int nextIndex;
		private Wrapper<K, V> lastReturned;
		private int expectedModCount;

		private LiveEntryIterator(Function<Wrapper<K, V>, T> mapper) {
			this.mapper = mapper;
			this.expectedModCount = modCount;
			this.nextIndex = nextValidFrom(0);
		}

		@Override
		public boolean hasNext() {
			return nextIndex < array.length;
		}

		@Override
		public T next() {
			checkForComodification();
			if (!hasNext()) {
				throw new NoSuchElementException();
			}
			lastReturned = array[nextIndex];
			nextIndex = nextValidFrom(nextIndex + 1);
			return mapper.apply(lastReturned);
		}

		@Override
		public void remove() {
			if (lastReturned == null) {
				throw new IllegalStateException("next() has not been called since the last remove()");
			}
			checkForComodification();
			lastReturned.markRemoved();
			size--;
			modCount++;
			expectedModCount = modCount;
			lastReturned = null;
		}

		private void checkForComodification() {
			if (modCount != expectedModCount) {
				throw new ConcurrentModificationException();
			}
		}

		private int nextValidFrom(int index) {
			int i = index;
			while (i < array.length && !valid(array[i])) {
				++i;
			}
			return i;
		}
	}

}
