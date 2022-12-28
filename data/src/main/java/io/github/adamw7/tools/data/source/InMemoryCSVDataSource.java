package io.github.adamw7.tools.data.source;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import io.github.adamw7.tools.data.source.interfaces.InMemoryDataSource;

public class InMemoryCSVDataSource extends CSVDataSource implements InMemoryDataSource {

	public InMemoryCSVDataSource(String fileName, int columnsRow) throws FileNotFoundException {
		super(fileName, DEFAULT_DELIMITER, columnsRow);
	}
	
	public InMemoryCSVDataSource(String fileName, String delimiter, int columnsRow) throws FileNotFoundException {
		super(fileName, delimiter, columnsRow);
	}

	@Override
	public List<String[]> read() throws IOException {
		open();
		List<String[]> data = new ArrayList<>();
		while (hasMoreData()) {
			data.add(nextRow());
		}
		return data;
	}

}
