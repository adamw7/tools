package io.github.adamw7.tools.data.structure;

import java.util.AbstractCollection;
import java.util.AbstractSet;
import java.util.Collection;
import java.util.Iterator;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.function.Function;

import io.github.adamw7.tools.data.structure.internal.DoubleHashing;
import io.github.adamw7.tools.data.structure.internal.Primes;
import io.github.adamw7.tools.data.structure.internal.Wrapper;

public class OpenAddressingMap<K, V> implements Map<K, V> {

	static final int DEFAULT_SIZE = DoubleHashing.DEFAULT_SIZE;
	int prime;

	protected Wrapper<K, V>[] array;
	protected int size;

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
	 */
	private Wrapper<K, V> find(Object key) {
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
		if (key == null) {
			throw new IllegalArgumentException("Key is null");
		}
		return DoubleHashing.sequence(key.hashCode(), prime, array.length);
	}

	@Override
	public V put(K key, V value) {
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

	private void resize() {
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
		return wrapper.getValue();
	}

	@Override
	public void putAll(Map<? extends K, ? extends V> map) {
		Set<? extends K> keys = map.keySet();
		for (K key : keys) {
			put(key, map.get(key));
		}
	}

	@Override
	public void clear() {
		initArray(size >= array.length ? newSize() : Math.max(size, 1));
		size = 0;
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
	 * the map instead of a throwaway copy.
	 */
	private final class LiveEntryIterator<T> implements Iterator<T> {

		private final Function<Wrapper<K, V>, T> mapper;
		private int nextIndex;
		private Wrapper<K, V> lastReturned;

		private LiveEntryIterator(Function<Wrapper<K, V>, T> mapper) {
			this.mapper = mapper;
			this.nextIndex = nextValidFrom(0);
		}

		@Override
		public boolean hasNext() {
			return nextIndex < array.length;
		}

		@Override
		public T next() {
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
			lastReturned.markRemoved();
			size--;
			lastReturned = null;
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
