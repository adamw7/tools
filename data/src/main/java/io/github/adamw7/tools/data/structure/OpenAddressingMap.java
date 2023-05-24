package io.github.adamw7.tools.data.structure;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

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
		for (int i = 0; i < array.length; ++i) {
			int hash = hash(key, i);
			Wrapper<K, V> wrapper = array[hash];
			
			if (valid(wrapper) && wrapper.key.equals(key)) {
				return wrapper.value;
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
		return hashCode % array.length;
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
				return value;
			} else if (wrapper.key.equals(key)) {
				array[hash] = new Wrapper<>(key, value); // overwrite
				return value;
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
		size = newSize();
		OpenAddressingMap<K, V> newMap = new OpenAddressingMap<>(size);
		newMap.putAll(this);
		clear();
		putAll(newMap);
		newMap.clear();
	}

	@Override
	public V remove(Object key) {
		for (int i = 0; i < array.length; ++i) {
			int hash = hash(key, i);
			Wrapper<K, V> wrapper = array[hash];
			
			if (wrapper.key.equals(key)) {
				wrapper.removed = true;
				size--;
				return wrapper.value;
			}
		}
		return null;
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
		Set<K> keys = new HashSet<>();
		for (int i = 0; i < array.length; ++i) {
			Wrapper<K, V> wrapper = array[i];
			if (valid(wrapper)) {
				keys.add(wrapper.key);
			}
		}
		return keys;
	}

	@Override
	public Collection<V> values() {
		List<V> values = new ArrayList<>();
		for (int i = 0; i < array.length; ++i) {
			Wrapper<K, V> wrapper = array[i];
			if (valid(wrapper)) {
				values.add(wrapper.value);
			}
		}
		return values;
	}

	private boolean valid(Wrapper<K, V> wrapper) {
		return wrapper != null && !wrapper.removed;
	}

	@Override
	public Set<Entry<K, V>> entrySet() {
		Set<Entry<K, V>> entrySet = HashSet.newHashSet(size);
		for (int i = 0; i < array.length; ++i) {
			Wrapper<K, V> wrapper = array[i];
			if (valid(wrapper)) {
				entrySet.add(wrapper);
			}
		}
		
		return entrySet;
	}

}
