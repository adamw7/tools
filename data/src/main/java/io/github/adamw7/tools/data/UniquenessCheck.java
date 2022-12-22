package io.github.adamw7.tools.data;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import io.github.adamw7.tools.data.interfaces.DataSource;

public class UniquenessCheck {
	
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

	private DataSource dataSource;
	
	public UniquenessCheck() {
		
	}

	public void setDataSource(DataSource dataSource) {
		this.dataSource = dataSource;
	}

	public Result exec(String...keyCandidates) throws Exception {
		dataSource.open();
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

	private Integer[] getIndiciesOf(String[] keyCandidates, String[] allColumns) {
		List<Integer> indicies = new ArrayList<>();
		
		for (int i = 0; i < allColumns.length; ++i) {
			for (int j = 0; j < keyCandidates.length; ++j) {
				if (allColumns[i].equals(keyCandidates[j])) {
					indicies.add(i);
				}
			}
		}
		
		return indicies.toArray(new Integer[] {});
	}

	private Key key(String[] keyCandidates, String[] row, Integer[] indicies) {
		List<String> values = new ArrayList<>(keyCandidates.length);
		
		for (Integer index : indicies) {
			values.add(row[index]);
		}
		
		return new Key(values.toArray(new String[keyCandidates.length]));
	}

	
	
}
