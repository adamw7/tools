package io.github.adamw7.tools.data.structure;

public class Wrapper<K, V> {


	K key;
	V value;
	boolean removed = false;
	
	public Wrapper(K key, V value) {
		this.key = key;
		this.value = value;
	}
	
}
