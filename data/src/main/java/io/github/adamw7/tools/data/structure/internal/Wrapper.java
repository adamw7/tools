package io.github.adamw7.tools.data.structure.internal;

import java.util.Map.Entry;

public class Wrapper<K, V> implements Entry<K, V>{


	public final K key;
	public V value;
	public boolean removed = false;
	
	public Wrapper(K key, V value) {
		this.key = key;
		this.value = value;
	}

	@Override
	public K getKey() {
		return key;
	}

	@Override
	public V getValue() {
		return value;
	}

	@Override
	public V setValue(V value) {
		this.value = value;
		return value;
	}
	
}
