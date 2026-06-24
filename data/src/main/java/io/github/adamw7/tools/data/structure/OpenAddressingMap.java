package io.github.adamw7.tools.data.structure;

import java.util.Arrays;
import java.util.Collection;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import io.github.adamw7.tools.data.structure.internal.Primes;
import io.github.adamw7.tools.data.structure.internal.Wrapper;

public class OpenAddressingMap<K, V> implements Map<K, V> {

	private static final double MULTIPLIER = 1.2;
	static final int DEFAULT_SIZE = 64;
	int prime;
	
	protected Wrapper<K, V>[] array;
	protected int size;
	
	public OpenAddressingMap(int size) {
		initArray(size);
	}

	@SuppressWarnings("unchecked")
	private void initArray(int size) {
		if (size <= 0) {
			throw new IllegalArgumentException("Wrong size: " + size);
		}
		int newSize = Math.max(size, 3); // array of size 2 would force prime = 1
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
		return wrapper == null ? null : wrapper.value;
	}

	/**
	 * Probes the double-hashing sequence for {@code key} and returns its live
	 * wrapper, or {@code null} when the key is absent. An empty (never-used) slot
	 * ends the search: {@link #put} always fills the first such slot, so the key
	 * cannot lie beyond it. Tombstones ({@code removed}) are skipped rather than
	 * treated as terminal, because live entries may sit past them.
	 */
	private Wrapper<K, V> find(Object key) {
		for (int i = 0; i < array.length; ++i) {
			Wrapper<K, V> wrapper = array[hash(key, i)];
			if (wrapper == null) {
				return null;
			}
			if (valid(wrapper) && wrapper.key.equals(key)) {
				return wrapper;
			}
		}
		return null;
	}

	private int hash(Object key, int iteration) {
		if (key == null) {
			throw new IllegalArgumentException("Key is null");
		} else {
			// double hashing
			int hashCode = key.hashCode();
			int h1 = h1(hashCode);
			int h2 = h2(hashCode);
			return Math.abs((h1 + (iteration * h2)) % array.length);
		}
	}

	private int h2(int hashCode) {
		return 1 + (Math.abs(hashCode) % (array.length - 1));
	}

	private int h1(int hashCode) {
		return prime - (hashCode % prime);
	}

	@Override
	public V put(K key, V value) {
		checkIfResizeNeeded();
		for (int i = 0; i < array.length; ++i) {
			int hash = hash(key, i);
			Wrapper<K, V> wrapper = array[hash];
			if (wrapper == null) {
				array[hash] = new Wrapper<>(key, value);
				size++;
				return null;
			} else if (wrapper.key.equals(key)) {
				V previous = wrapper.value;
				array[hash] = new Wrapper<>(key, value);
				return previous;
			} // removed are skipped
		}
		resize();
		return put(key, value);
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
				put(wrapper.key, wrapper.value);
			}
		}
	}

	@Override
	public V remove(Object key) {
		Wrapper<K, V> wrapper = find(key);
		if (wrapper == null) {
			return null;
		}
		wrapper.removed = true;
		size--;
		return wrapper.value;
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
		initArray(size >= array.length ? newSize() : size);
		size = 0;
	}

	private int newSize() {
		return (int) (array.length * MULTIPLIER);
	}

	@Override
	public Set<K> keySet() {
		return validWrappers().map(wrapper -> wrapper.key).collect(Collectors.toSet());
	}

	@Override
	public Collection<V> values() {
		return validWrappers().map(wrapper -> wrapper.value).collect(Collectors.toList());
	}

	@Override
	public Set<Entry<K, V>> entrySet() {
		return validWrappers().<Entry<K, V>>map(wrapper -> wrapper).collect(Collectors.toSet());
	}

	private Stream<Wrapper<K, V>> validWrappers() {
		return Arrays.stream(array).filter(this::valid);
	}

	private boolean valid(Wrapper<K, V> wrapper) {
		return wrapper != null && !wrapper.removed;
	}

}
