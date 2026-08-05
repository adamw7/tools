package io.github.adamw7.tools.data.uniqueness;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import io.github.adamw7.tools.data.source.interfaces.ColumnarDataSource;
import io.github.adamw7.tools.data.source.interfaces.IterableDataSource;

public abstract class AbstractUniqueness<T extends ColumnarDataSource> implements Uniqueness {

	private static final Logger log = LogManager.getLogger(AbstractUniqueness.class);

	protected final T dataSource;

	protected AbstractUniqueness(T dataSource) {
		if (dataSource == null) {
			throw new IllegalArgumentException("dataSource must not be null");
		}
		this.dataSource = dataSource;
	}

	/**
	 * The positions the named columns sit at in the open source, once it is confirmed
	 * to declare every one of them. The two questions are asked of the same reading of
	 * the column names, so a source that answers slowly is only asked once.
	 */
	protected int[] indicesOf(String[] keyCandidates) {
		String[] allColumns = dataSource.getColumnNames();
		checkIfCandidatesExistIn(keyCandidates, allColumns);
		return IntStream.range(0, allColumns.length)
				.flatMap(index -> Arrays.stream(keyCandidates)
						.filter(candidate -> allColumns[index].equalsIgnoreCase(candidate))
						.mapToInt(candidate -> index))
				.toArray();
	}

	private void checkIfCandidatesExistIn(String[] keyCandidates, String[] allColumns) {
		Set<String> all = Arrays.stream(allColumns)
				.map(column -> column == null ? null : column.toLowerCase())
				.collect(Collectors.toCollection(HashSet::new));

		for (String candidate : keyCandidates) {
			if (!all.contains(candidate.toLowerCase())) {
				throw new ColumnNotFoundException(candidate + " cannot be found in " + Arrays.toString(allColumns));
			}
		}
	}


	protected void check(String[] keyCandidates) {
		handleNullsAndEmpty(keyCandidates);
		handleDuplicates(keyCandidates);
	}

	private void handleNullsAndEmpty(String[] keyCandidates) {
		if (keyCandidates == null || keyCandidates.length == 0) {
			throw new IllegalArgumentException("Wrong input: " + Arrays.toString(keyCandidates));
		}
		if (Arrays.stream(keyCandidates).anyMatch(Objects::isNull)) {
			throw new IllegalArgumentException("Input columns cannot be null");
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

	/**
	 * Runs the uniqueness check over the already-open {@link #dataSource}. Both public
	 * entry points open the source exactly once before calling this — {@link #exec}
	 * directly, {@link #execForAllColumns} while reading the column names — because
	 * the open-once map-backed in-memory sources reject a second open with
	 * {@code DataSource is already open}.
	 *
	 * <p>Implementations must not close the source: the subset search that follows a
	 * successful check {@link IterableDataSource#reset() resets} it between passes,
	 * and a source that owns its connection (the Parquet ones) cannot be reopened once
	 * closed. The public entry points close it exactly once, after the whole check —
	 * subsets included — has finished.
	 */
	protected abstract Result execOnOpenSource(String[] keyCandidates);

	/**
	 * The columns are validated before the source is opened, since a caller's input is
	 * wrong whatever the source holds, and a rejected call should leave the source as it
	 * found it.
	 */
	@Override
	public final Result exec(String... keyCandidates) {
		check(keyCandidates);
		return onOpenSource(source -> keyCandidates);
	}

	/** The columns can only be named once the source is open, so they are checked there. */
	@Override
	public final Result execForAllColumns() {
		return onOpenSource(source -> checked(source.getColumnNames()));
	}

	private Result onOpenSource(Function<T, String[]> keyCandidates) {
		dataSource.open();
		try {
			return execOnOpenSource(keyCandidates.apply(dataSource));
		} finally {
			close(dataSource);
		}
	}

	private String[] checked(String[] keyCandidates) {
		check(keyCandidates);
		return keyCandidates;
	}
}