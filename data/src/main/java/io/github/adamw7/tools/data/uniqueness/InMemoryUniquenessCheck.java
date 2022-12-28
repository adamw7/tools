package io.github.adamw7.tools.data.uniqueness;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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

	private Result findUnique(Integer[] inidices, String... keyCandidates) throws IOException {
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
		return new Result(true, keyCandidates);
	}
	
	@Override
	public <T extends IterableDataSource> void setDataSource(T source) {
		this.dataSource = (InMemoryDataSource)source;
	}
}
