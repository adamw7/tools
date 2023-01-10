package io.github.adamw7.tools.data.uniqueness;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class KeyFinder {

	private final Integer[] indicies;
	private final Set<Key> set = new HashSet<>();

	public KeyFinder(Integer[] indicies) {
		this.indicies = indicies;
	}

	public boolean found(String[] row) {
		if (row != null) {
			Key key = key(row, indicies);
			if (set.contains(key)) {
				return true;
			} else {
				set.add(key);
			}
		}
		return false;
	}
	
	protected Key key(String[] row, Integer[] indicies) {
		List<String> values = new ArrayList<>(indicies.length);

		for (Integer index : indicies) {
			values.add(row[index]);
		}

		return new Key(values.toArray(new String[indicies.length]));
	}

}
