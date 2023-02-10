package io.github.adamw7.tools.data.structure;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class OpenAddressingMap<K, V> implements Map<K, V> {
	
	private static final int DEFAULT_SIZE = 64;
	protected Wrapper[] array;
	protected int size;
	
	public OpenAddressingMap(int size) {
		initArray(size);
	}

	private void initArray(int size) {
		array = new Wrapper[size];
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
			Wrapper<K, V> wrapper = (Wrapper<K, V>)array[hash];
			
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
			return Math.abs((hashCode + (iteration * hashCode)) % array.length);
		}
	}

	@Override
	public V put(K key, V value) {
		for (int i = 0; i < array.length; ++i) {
			int hash = hash(key, i);
			Wrapper<K, V> wrapper = (Wrapper<K, V>)array[hash];			
			if (wrapper == null) {
				array[hash] = new Wrapper<K, V>(key, value);
				size++;
				return value;
			} else if (wrapper.removed) {
				continue;
			} else if (wrapper.key.equals(key)) {
				array[hash] = new Wrapper<K, V>(key, value); // overwrite
				return value;
			}
		}
		return null;
	}
	

	@Override
	public V remove(Object key) {
		for (int i = 0; i < array.length; ++i) {
			int hash = hash(key, i);
			Wrapper<K, V> wrapper = (Wrapper<K, V>)array[hash];
			
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
		initArray(DEFAULT_SIZE);
		size = 0;
	}

	@Override
	public Set<K> keySet() {
		Set<K> keys = new HashSet<>();
		for (int i = 0; i < array.length; ++i) {
			Wrapper<K, V> wrapper = (Wrapper<K, V>)array[i];
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
			Wrapper<K, V> wrapper = (Wrapper<K, V>)array[i];
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
		// TODO Auto-generated method stub
		return null;
	}

}