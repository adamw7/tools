package io.github.adamw7.tools.data.uniqueness;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

public class KeyFinder {

	private final int[] indices;
	private final Set<Key> set = new HashSet<>();

	public KeyFinder(int[] indices) {
		this.indices = indices;
	}

	public boolean found(String[] row) {
		return row != null && !set.add(key(row));
	}

	protected Key key(String[] row) {
		return new Key(Arrays.stream(indices).mapToObj(index -> row[index]).toArray(String[]::new));
	}
}
