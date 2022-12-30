package io.github.adamw7.tools.data.uniqueness;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import io.github.adamw7.tools.data.source.interfaces.InMemoryDataSource;
import io.github.adamw7.tools.data.source.interfaces.IterableDataSource;

public class InMemoryUniquenessCheck extends Uniqueness {
	
	public InMemoryUniquenessCheck() {}

	public InMemoryUniquenessCheck(InMemoryDataSource dataSource) {
		setDataSource(dataSource);
	}
	
	@Override
	public Result exec(String... keyCandidates) throws Exception {
		check(keyCandidates);
		dataSource.open();
		checkIfCandidatesExistIn(keyCandidates, dataSource.getColumnNames());

		Integer[] inidices = getIndiciesOf(keyCandidates, dataSource.getColumnNames());
		return findUnique(inidices, keyCandidates);
	}

	private Result findUnique(Integer[] inidices, String... keyCandidates) throws Exception {
		List<String[]> data = ((InMemoryDataSource) dataSource).read();
		Map<Key, String[]> map = new HashMap<>();
		for (String[] row : data) {
			if (row != null) {
				Key key = key(keyCandidates, row, inidices);
				if (map.get(key) != null) {
					return new Result(false, keyCandidates, row);
				} else {
					map.put(key, row);
				}
			}
		}
		dataSource.close();
		return handleSucessfullCheck(keyCandidates);
	}
	
	protected List<Result> findPotentiallySmallerSetOfCanidates(String[] keyCandidates) throws Exception {
		List<Result> list = new ArrayList<>();
		for (String candidate : keyCandidates) {
			Set<String> set = new HashSet<>();
			set.addAll(Arrays.asList(keyCandidates));
			set.remove(candidate);
			if (!set.isEmpty()) {
				String[] newCandidates = set.toArray(new String[keyCandidates.length - 1]);
				Integer[] inidices = getIndiciesOf(newCandidates, dataSource.getColumnNames());
				Result result = findUnique(inidices, newCandidates);
				if (result.unique) {
					list.add(result);
				}
			}
		}
		return list;
	}
	
	@Override
	public <T extends IterableDataSource> void setDataSource(T source) {
		this.dataSource = (InMemoryDataSource)source;
	}
}
