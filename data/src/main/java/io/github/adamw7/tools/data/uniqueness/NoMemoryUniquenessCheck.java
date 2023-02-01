package io.github.adamw7.tools.data.uniqueness;

import java.util.HashSet;
import java.util.Set;

import io.github.adamw7.tools.data.source.interfaces.IterableDataSource;

public class NoMemoryUniquenessCheck extends AbstractUniqueness {

	public NoMemoryUniquenessCheck() { }
	
	public NoMemoryUniquenessCheck(IterableDataSource dataSource) {
		this.dataSource = dataSource;
	}

	@Override
	public Result exec(String... keyCandidates) {
		check(keyCandidates);
		dataSource.open();
		checkIfCandidatesExistIn(keyCandidates, dataSource.getColumnNames());
		Integer[] inidices = getIndiciesOf(keyCandidates, dataSource.getColumnNames());
		KeyFinder finder = new KeyFinder(inidices);
		while (dataSource.hasMoreData()) {
			String[] row = dataSource.nextRow();
			if (finder.found(row)) {
				return new Result(false, keyCandidates, row);
			}
		}
		
		Result result = handleSucessfullCheck(keyCandidates);
		close(dataSource);
		return result;
	}

	protected Set<Result> findPotentiallySmallerSetOfCanidates(String[] keyCandidates) {
		Set<Result> uniqueCanidates = new HashSet<>();
		for (String candidate : keyCandidates) {
			Set<String> set = createSmallerSet(keyCandidates, candidate);
			if (!set.isEmpty()) {
				dataSource.reset();
				Result result = exec(set.toArray(new String[keyCandidates.length - 1]));
				if (result.unique) {
					uniqueCanidates.add(result);
				}
			}
		}
		return uniqueCanidates;
	}

	@Override
	public <T extends IterableDataSource> void setDataSource(T source) {
		this.dataSource = source;
	}


}
