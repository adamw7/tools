package io.github.adamw7.tools.data.uniqueness;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import io.github.adamw7.tools.data.source.interfaces.IterableDataSource;

public abstract class AbstractUniqueness implements Uniqueness {
	
	protected IterableDataSource dataSource;

	protected void checkIfCandidatesExistIn(String[] keyCandidates, String[] allColumns) {
		Set<String> all = new HashSet<>(Arrays.asList(toLower(allColumns)));

		for (String candidate : keyCandidates) {
			if (!all.contains(candidate.toLowerCase())) {
				throw new ColumnNotFoundException(candidate + " cannot be found in " + Arrays.toString(allColumns));
			}
		}
	}
	
	protected String[] toLower(String[] items) {
		String[] arrayToLower = new String[items.length];
		for (int i = 0; i < items.length; ++i) {
			arrayToLower[i] = items[i] == null ? null : items[i].toLowerCase();
		}
		return arrayToLower;
	}

	protected Integer[] getIndiciesOf(String[] keyCandidates, String[] allColumns) {
		List<Integer> indicies = new ArrayList<>();

		for (int i = 0; i < allColumns.length; ++i) {
			for (int j = 0; j < keyCandidates.length; ++j) {
				if (allColumns[i].equalsIgnoreCase(keyCandidates[j])) {
					indicies.add(i);
				}
			}
		}

		return indicies.toArray(new Integer[keyCandidates.length]);
	}
	
	protected void check(String[] keyCandidates) {
		handleNullsAndEmpty(keyCandidates);
		handleDuplicates(keyCandidates);
	}

	private void handleNullsAndEmpty(String[] keyCandidates) {
		if (keyCandidates == null || keyCandidates.length == 0) {
			throw new IllegalArgumentException("Wrong input: " + Arrays.toString(keyCandidates));
		}
		for (String candidate : keyCandidates) {
			if (candidate == null) {
				throw new IllegalArgumentException("Input columns cannot be null");
			}
		}
	}

	private void handleDuplicates(String[] keyCandidates) {
		Set<String> set = new HashSet<>();
		for (String candidate : keyCandidates) {
			if (set.contains(candidate)) {
				throw new IllegalArgumentException("Duplicate in input: " + candidate);
			} else {
				set.add(candidate);
			}
		}
	}
	
	protected Result handleSuccessfullCheck(String[] keyCandidates) {
		Set<Result> set = findPotentiallySmallerSetOfCandidates(keyCandidates);
		return new Result(true, keyCandidates, null, set);
	}
	
	protected Set<String> createSmallerSet(String[] keyCandidates, String candidate) {
		Set<String> set = new HashSet<>(Arrays.asList(keyCandidates));
		set.remove(candidate);
		return set;
	}
	
	protected void close(IterableDataSource dataSource) {
		try {
			dataSource.close();
		} catch (Exception ignored) {}
	}
	
	protected abstract Set<Result> findPotentiallySmallerSetOfCandidates(String[] keyCandidates);
}