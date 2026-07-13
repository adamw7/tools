package io.github.adamw7.tools.data.uniqueness;

import java.util.List;

import io.github.adamw7.tools.data.source.interfaces.InMemoryDataSource;

public class InMemoryUniquenessCheck extends AbstractUniqueness<InMemoryDataSource> {

	public InMemoryUniquenessCheck(InMemoryDataSource dataSource) {
		super(dataSource);
	}

	@Override
	public Result exec(String... keyCandidates) {
		check(keyCandidates);
		dataSource.open();
		return execOnOpenSource(keyCandidates);
	}

	@Override
	protected Result execOnOpenSource(String[] keyCandidates) {
		checkIfCandidatesExistIn(keyCandidates, dataSource.getColumnNames());

		int[] indices = getIndiciesOf(keyCandidates, dataSource.getColumnNames());
		return findUnique(indices, keyCandidates);
	}

	private Result findUnique(int[] indices, String... keyCandidates) {
		dataSource.reset();
		List<String[]> data = dataSource.readAll();
		close(dataSource);
		KeyFinder finder = new KeyFinder(indices);
		for (String[] row : data) {
			if (finder.found(row)) {
				return new Result(false, keyCandidates, row);
			}
		}

		return handleSuccessfulCheck(keyCandidates);
	}

	@Override
	protected Result checkSubset(String[] newCandidates) {
		int[] indices = getIndiciesOf(newCandidates, dataSource.getColumnNames());
		return findUnique(indices, newCandidates);
	}
}
