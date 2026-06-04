package io.github.adamw7.tools.data.uniqueness;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

public class KeyFinder {

	private final Integer[] indices;
	private final Set<Key> set = new HashSet<>();

	public KeyFinder(Integer[] indices) {
		this.indices = indices;
	}

	public boolean found(String[] row) {
		if (row == null) {
			return false;
		}
		return !set.add(key(row, indices));
	}

	protected Key key(String[] row, Integer[] indices) {
		String[] values = Arrays.stream(indices).map(index -> row[index]).toArray(String[]::new);
		return new Key(values);
	}

}
