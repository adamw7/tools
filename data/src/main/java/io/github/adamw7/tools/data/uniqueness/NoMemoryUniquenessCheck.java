package io.github.adamw7.tools.data.uniqueness;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import io.github.adamw7.tools.data.source.interfaces.IterableDataSource;

public class NoMemoryUniquenessCheck extends Uniqueness {

	private final IterableDataSource dataSource;

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

	private Result handleSucessfullCheck(String[] keyCandidates) throws Exception {
		List<Result> list = findPotentiallySmallerSetOfCanidates(keyCandidates);
		return new Result(true, keyCandidates, null, list);
	}

	private List<Result> findPotentiallySmallerSetOfCanidates(String[] keyCandidates) throws Exception {
		List<Result> list = new ArrayList<>();
		for (String candidate : keyCandidates) {
			Set<String> set = new HashSet<>();
			set.addAll(Arrays.asList(keyCandidates));
			set.remove(candidate);
			if (!set.isEmpty()) {
				dataSource.reset();
				Result result = exec(set.toArray(new String[keyCandidates.length - 1]));
				if (result.unique) {
					list.add(result);
				}
			}
		}
		return list;
	}


}
