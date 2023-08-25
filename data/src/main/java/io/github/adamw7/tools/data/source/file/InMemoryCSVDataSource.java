package io.github.adamw7.tools.data.source.file;

import java.io.FileNotFoundException;
import java.util.List;

import io.github.adamw7.tools.data.source.interfaces.InMemoryDataSource;

public class InMemoryCSVDataSource extends CSVDataSource implements InMemoryDataSource {

	public InMemoryCSVDataSource(String fileName, int columnsRow) throws FileNotFoundException {
		super(fileName, DEFAULT_DELIMITER, columnsRow);
	}
	
	public InMemoryCSVDataSource(String fileName, String delimiter, int columnsRow) throws FileNotFoundException {
		super(fileName, delimiter, columnsRow);
	}

	public InMemoryCSVDataSource(String fileName) throws FileNotFoundException {
		super(fileName, DEFAULT_DELIMITER, -1);
	}

	@Override
	public List<String[]> readAll() {
		return super.readAll();
	}

}
