package io.github.adamw7.tools.data.structure;

import java.util.Arrays;
import java.util.Collection;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

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
		return get(key) != null;
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
		return validWrappers().map(wrapper -> wrapper.getKey()).collect(Collectors.toSet());
	}

	@Override
	public Collection<V> values() {
		return validWrappers().map(wrapper -> wrapper.getValue()).collect(Collectors.toList());
	}

	@Override
	public Set<Entry<K, V>> entrySet() {
		return validWrappers().<Entry<K, V>>map(wrapper -> wrapper).collect(Collectors.toSet());
	}

	private Stream<Wrapper<K, V>> validWrappers() {
		return Arrays.stream(array).filter(this::valid);
	}

	private boolean valid(Wrapper<K, V> wrapper) {
		return wrapper != null && !wrapper.isRemoved();
	}

}
