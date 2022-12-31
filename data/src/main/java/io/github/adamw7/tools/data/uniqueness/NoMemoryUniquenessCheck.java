package io.github.adamw7.tools.data.uniqueness;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import io.github.adamw7.tools.data.source.interfaces.IterableDataSource;

public class NoMemoryUniquenessCheck extends Uniqueness {

	public NoMemoryUniquenessCheck() { }
	
	public NoMemoryUniquenessCheck(IterableDataSource dataSource) {
		this.dataSource = dataSource;
	}

	@Override
	public Result exec(String... keyCandidates) throws Exception {
		check(keyCandidates);
		dataSource.open();
		checkIfCandidatesExistIn(keyCandidates, dataSource.getColumnNames());
		Map<Key, String[]> map = new HashMap<>();
		Integer[] inidices = getIndiciesOf(keyCandidates, dataSource.getColumnNames());
		while (dataSource.hasMoreData()) {
			String[] row = dataSource.nextRow();

			if (row != null) {
				Key key = key(keyCandidates, row, inidices);
				if (map.get(key) != null) {
					return new Result(false, keyCandidates, row);
				} else {
					map.put(key, row);
				}
			}
		}

		Result result = handleSucessfullCheck(keyCandidates);
		dataSource.close();
		return result;
	}

	protected Set<Result> findPotentiallySmallerSetOfCanidates(String[] keyCandidates) throws Exception {
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
