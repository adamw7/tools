package io.github.adamw7.tools.data.uniqueness;

import io.github.adamw7.tools.data.source.interfaces.ColumnarDataSource;

public class NoMemoryUniquenessCheck extends AbstractUniqueness<ColumnarDataSource> {

	public NoMemoryUniquenessCheck(ColumnarDataSource dataSource) {
		super(dataSource);
	}

	@Override
	public Result exec(String... keyCandidates) {
		check(keyCandidates);
		dataSource.open();
		try {
			return execOnOpenSource(keyCandidates);
		} finally {
			close(dataSource);
		}
	}

	@Override
	protected Result execOnOpenSource(String[] keyCandidates) {
		checkIfCandidatesExistIn(keyCandidates, dataSource.getColumnNames());
		int[] indices = getIndiciesOf(keyCandidates, dataSource.getColumnNames());
		KeyFinder finder = new KeyFinder(indices);
		while (dataSource.hasMoreData()) {
			String[] row = dataSource.nextRow();
			if (finder.found(row)) {
				return new Result(false, keyCandidates, row);
			}
		}

		return handleSuccessfulCheck(keyCandidates);
	}

	@Override
	protected Result checkSubset(String[] newCandidates) {
		// The caller has just reset() the source, so it is open again; going
		// through exec() would close it after this subset and leave nothing for
		// the next one to reset.
		return execOnOpenSource(newCandidates);
	}
}
