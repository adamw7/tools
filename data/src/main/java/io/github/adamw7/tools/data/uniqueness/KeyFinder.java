package io.github.adamw7.tools.data.uniqueness;

import java.util.HashSet;
import java.util.Set;

public class KeyFinder {

	private final int[] indices;
	private final Set<Key> set = new HashSet<>();

	public KeyFinder(int[] indices) {
		this.indices = indices;
	}

	public boolean found(String[] row) {
		if (row == null) {
			return false;
		}
		return !set.add(key(row, indices));
	}

	protected Key key(String[] row, int[] indices) {
		String[] values = new String[indices.length];
		for (int i = 0; i < indices.length; ++i) {
			values[i] = row[indices[i]];
		}
		return new Key(values);
	}

}
