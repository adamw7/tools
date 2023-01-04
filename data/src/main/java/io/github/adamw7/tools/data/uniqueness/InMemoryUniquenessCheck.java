package io.github.adamw7.tools.data.uniqueness;

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
		List<String[]> data = ((InMemoryDataSource) dataSource).readAll();
		dataSource.close();
		Map<Key, String[]> map = new HashMap<>();
		for (String[] row : data) {
			Result result = processRow(map, row, keyCandidates, inidices);
			if (result != null) {
				return result;
			}
		}

		return handleSucessfullCheck(keyCandidates);
	}

	protected Set<Result> findPotentiallySmallerSetOfCanidates(String[] keyCandidates) throws Exception {
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
		this.dataSource = (InMemoryDataSource)source;
	}
}
