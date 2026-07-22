package io.github.adamw7.tools.data.structure.internal;

import java.util.Map.Entry;
import java.util.Objects;

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
		V previous = this.value;
		this.value = value;
		return previous;
	}

	public boolean isRemoved() {
		return removed;
	}

	public void markRemoved() {
		this.removed = true;
	}

	@Override
	public boolean equals(Object other) {
		if (this == other) {
			return true;
		}
		if (!(other instanceof Entry<?, ?> entry)) {
			return false;
		}
		return Objects.equals(key, entry.getKey()) && Objects.equals(value, entry.getValue());
	}

	@Override
	public int hashCode() {
		return Objects.hashCode(key) ^ Objects.hashCode(value);
	}

}
