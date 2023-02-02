package io.github.adamw7.tools.data.uniqueness;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import io.github.adamw7.tools.data.source.interfaces.InMemoryDataSource;
import io.github.adamw7.tools.data.source.interfaces.IterableDataSource;

public class InMemoryUniquenessCheck extends AbstractUniqueness {

	public InMemoryUniquenessCheck() {
	}

	public InMemoryUniquenessCheck(InMemoryDataSource dataSource) {
		setDataSource(dataSource);
	}

	@Override
	public Result exec(String... keyCandidates) {
		check(keyCandidates);
		dataSource.open();
		checkIfCandidatesExistIn(keyCandidates, dataSource.getColumnNames());

		Integer[] inidices = getIndiciesOf(keyCandidates, dataSource.getColumnNames());
		return findUnique(inidices, keyCandidates);
	}

	private Result findUnique(Integer[] inidices, String... keyCandidates) {
		List<String[]> data = ((InMemoryDataSource) dataSource).readAll();
		close(dataSource);
		KeyFinder finder = new KeyFinder(inidices);
		for (String[] row : data) {
			if (finder.found(row)) {
				return new Result(false, keyCandidates, row);
			}
		}

		return handleSucessfullCheck(keyCandidates);
	}

	protected Set<Result> findPotentiallySmallerSetOfCanidates(String[] keyCandidates) {
		Set<Result> uniqueCanidates = new HashSet<>();
		for (String candidate : keyCandidates) {
			Set<String> set = createSmallerSet(keyCandidates, candidate);
			if (!set.isEmpty()) {
				dataSource.reset();
				String[] newCandidates = set.toArray(new String[keyCandidates.length - 1]);
				Integer[] inidices = getIndiciesOf(newCandidates, dataSource.getColumnNames());
				Result result = findUnique(inidices, newCandidates);
				if (result.unique) {
					uniqueCanidates.add(result);
				}
			}
		}

		return uniqueCanidates;
	}

	@Override
	public <T extends IterableDataSource> void setDataSource(T source) {
		if (source instanceof InMemoryDataSource) {
			this.dataSource = source;
		} else {
			String message = source == null ? null : source.getClass().getSimpleName();
			throw new IllegalArgumentException("Expected InMemoryDataSource and got: " + message);
		}
	}
}
