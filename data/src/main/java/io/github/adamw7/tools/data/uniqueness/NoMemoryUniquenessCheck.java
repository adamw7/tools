package io.github.adamw7.tools.data.uniqueness;

import io.github.adamw7.tools.data.source.interfaces.IterableDataSource;

public class NoMemoryUniquenessCheck extends AbstractUniqueness<IterableDataSource> {

	public NoMemoryUniquenessCheck(IterableDataSource dataSource) {
		super(dataSource);
	}

	@Override
	public Result exec(String... keyCandidates) {
		check(keyCandidates);
		dataSource.open();
		checkIfCandidatesExistIn(keyCandidates, dataSource.getColumnNames());
		Integer[] indices = getIndiciesOf(keyCandidates, dataSource.getColumnNames());
		KeyFinder finder = new KeyFinder(indices);
		while (dataSource.hasMoreData()) {
			String[] row = dataSource.nextRow();
			if (finder.found(row)) {
				close(dataSource);
				return new Result(false, keyCandidates, row);
			}
		}

		Result result = handleSuccessfulCheck(keyCandidates);
		close(dataSource);
		return result;
	}

	@Override
	protected Result checkSubset(String[] newCandidates) {
		return exec(newCandidates);
	}
}
