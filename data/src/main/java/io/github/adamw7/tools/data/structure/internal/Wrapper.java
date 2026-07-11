package io.github.adamw7.tools.data.structure.internal;

import java.util.Map.Entry;

public class Wrapper<K, V> implements Entry<K, V>{


	private final K key;
	private V value;
	private boolean removed = false;

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

	public boolean isRemoved() {
		return removed;
	}

	public void markRemoved() {
		this.removed = true;
	}

}
