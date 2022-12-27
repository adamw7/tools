package io.github.adamw7.tools.data.uniqueness;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public abstract class Uniqueness {

	public abstract Result exec(String... keyCandidates) throws Exception;

	protected void checkIfCandidatesExistIn(String[] keyCandidates, String[] allColumns) {
		Set<String> all = new HashSet<>(Arrays.asList(allColumns));

		for (String candidate : keyCandidates) {
			if (!all.contains(candidate)) {
				throw new ColumnNotFoundException(candidate + " cannot be found in " + Arrays.toString(allColumns));
			}
		}
	}
	
	protected Key key(String[] keyCandidates, String[] row, Integer[] indicies) {
		List<String> values = new ArrayList<>(keyCandidates.length);

		for (Integer index : indicies) {
			values.add(row[index]);
		}

		return new Key(values.toArray(new String[keyCandidates.length]));
	}
	
	protected Integer[] getIndiciesOf(String[] keyCandidates, String[] allColumns) {
		List<Integer> indicies = new ArrayList<>();

		for (int i = 0; i < allColumns.length; ++i) {
			for (int j = 0; j < keyCandidates.length; ++j) {
				if (allColumns[i].equals(keyCandidates[j])) {
					indicies.add(i);
				}
			}
		}

		return indicies.toArray(new Integer[keyCandidates.length]);
	}
	
	protected void check(String[] keyCandidates) {
		if (keyCandidates == null || keyCandidates.length == 0) {
			throw new IllegalArgumentException("Wrong input: " + Arrays.toString(keyCandidates));
		}
		for (String canidate : keyCandidates) {
			if (canidate == null) {
				throw new IllegalArgumentException("Input columns cannot be null");
			}
		}
	}
}