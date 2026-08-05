package io.github.adamw7.tools.data.uniqueness;

import io.github.adamw7.tools.data.source.interfaces.InMemoryDataSource;

public class InMemoryUniquenessCheck extends AbstractUniqueness<InMemoryDataSource> {

	public InMemoryUniquenessCheck(InMemoryDataSource dataSource) {
		super(dataSource);
	}

	@Override
	protected Result execOnOpenSource(String[] keyCandidates) {
		return findUnique(keyCandidates);
	}

	@Override
	protected Result checkSubset(String[] newCandidates) {
		return findUnique(newCandidates);
	}

	private Result findUnique(String... keyCandidates) {
		KeyFinder finder = new KeyFinder(indicesOf(keyCandidates));
		dataSource.reset();
		for (String[] row : dataSource.readAll()) {
			if (finder.found(row)) {
				return new Result(false, keyCandidates, row);
			}
		}

		return handleSuccessfulCheck(keyCandidates);
	}
}
