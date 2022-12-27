package io.github.adamw7.tools.data;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import io.github.adamw7.tools.data.interfaces.DataSource;

public class UniquenessCheck {
	
	private final DataSource dataSource;
	
	public static class Result {
		final boolean unique;
		final String[] columns;
		final String[] row;
		
		public Result(boolean unique, String[] columns, String[] row) {
			this.unique = unique;
			this.columns = columns;
			this.row = row;
		}
	}
	
	public UniquenessCheck(DataSource dataSource) {
		this.dataSource = dataSource;
	}

	public Result exec(String...keyCandidates) throws Exception {
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
		dataSource.close();
		return new Result(true, keyCandidates, null);
	}

	private void check(String[] keyCandidates) {
		if (keyCandidates == null || keyCandidates.length == 0) {
			throw new IllegalArgumentException("Wrong input: " + Arrays.toString(keyCandidates));
		}
		for (String canidate : keyCandidates) {
			if (canidate == null) {
				throw new IllegalArgumentException("Input columns cannot be null");
			}
		}
	}

	private void checkIfCandidatesExistIn(String[] keyCandidates, String[] allColumns) {
		Set<String> all =  new HashSet<>(Arrays.asList(allColumns));
		
		for (String candidate : keyCandidates) {
			if (!all.contains(candidate)) {
				throw new ColumnNotFoundException(candidate + " cannot be found in " + Arrays.toString(allColumns));
			}
		}
	}

	private Integer[] getIndiciesOf(String[] keyCandidates, String[] allColumns) {
		List<Integer> indicies = new ArrayList<>();
		
		for (int i = 0; i < allColumns.length; ++i) {
			for (int j = 0; j < keyCandidates.length; ++j) {
				if (allColumns[i].equals(keyCandidates[j])) {
					indicies.add(i);
				}
			}
		}
		
		return indicies.toArray(new Integer[keyCandidates.length]);
	}

	private Key key(String[] keyCandidates, String[] row, Integer[] indicies) {
		List<String> values = new ArrayList<>(keyCandidates.length);
		
		for (Integer index : indicies) {
			values.add(row[index]);
		}
		
		return new Key(values.toArray(new String[keyCandidates.length]));
	}

	
	
}
