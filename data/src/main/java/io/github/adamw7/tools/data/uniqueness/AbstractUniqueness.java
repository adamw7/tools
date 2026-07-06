package io.github.adamw7.tools.data.uniqueness;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import io.github.adamw7.tools.data.source.interfaces.IterableDataSource;

public abstract class AbstractUniqueness<T extends IterableDataSource> implements Uniqueness {

	private static final Logger log = LogManager.getLogger(AbstractUniqueness.class);

	protected final T dataSource;

	protected AbstractUniqueness(T dataSource) {
		if (dataSource == null) {
			throw new IllegalArgumentException("dataSource must not be null");
		}
		this.dataSource = dataSource;
	}

	protected void checkIfCandidatesExistIn(String[] keyCandidates, String[] allColumns) {
		Set<String> all = new HashSet<>(Arrays.asList(toLower(allColumns)));

		for (String candidate : keyCandidates) {
			if (!all.contains(candidate.toLowerCase())) {
				throw new ColumnNotFoundException(candidate + " cannot be found in " + Arrays.toString(allColumns));
			}
		}
	}
	
	protected String[] toLower(String[] items) {
		return Arrays.stream(items)
				.map(item -> item == null ? null : item.toLowerCase())
				.toArray(String[]::new);
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
			if (!set.add(candidate)) {
				throw new IllegalArgumentException("Duplicate in input: " + candidate);
			}
		}
	}
	
	protected Result handleSuccessfulCheck(String[] keyCandidates) {
		Set<Result> set = findPotentiallySmallerSetOfCandidates(keyCandidates);
		return new Result(true, keyCandidates, null, set);
	}

	private Set<Result> findPotentiallySmallerSetOfCandidates(String[] keyCandidates) {
		Set<Result> uniqueCandidates = new HashSet<>();
		for (String candidate : keyCandidates) {
			Set<String> smaller = createSmallerSet(keyCandidates, candidate);
			addIfUnique(uniqueCandidates, smaller, keyCandidates.length);
		}
		return uniqueCandidates;
	}

	private void addIfUnique(Set<Result> uniqueCandidates, Set<String> smaller, int originalSize) {
		if (smaller.isEmpty()) {
			return;
		}
		dataSource.reset();
		String[] newCandidates = smaller.toArray(new String[originalSize - 1]);
		Result result = checkSubset(newCandidates);
		if (result.unique) {
			uniqueCandidates.add(result);
		}
	}

	private Set<String> createSmallerSet(String[] keyCandidates, String candidate) {
		Set<String> set = new HashSet<>(Arrays.asList(keyCandidates));
		set.remove(candidate);
		return set;
	}

	protected void close(IterableDataSource dataSource) {
		try {
			dataSource.close();
		} catch (Exception e) {
			log.warn("Failed to close data source: {}", dataSource, e);
		}
	}

	protected abstract Result checkSubset(String[] newCandidates);
	
	@Override
	public Result execForAllColumns() {
		dataSource.open();
		return exec(dataSource.getColumnNames());
	}
}