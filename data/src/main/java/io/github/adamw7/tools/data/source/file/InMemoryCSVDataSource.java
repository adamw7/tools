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

	public InMemoryCSVDataSource(String fileName, AllowedPaths allowedPaths) throws FileNotFoundException {
		super(fileName, DEFAULT_DELIMITER, -1, allowedPaths);
	}

	public InMemoryCSVDataSource(String fileName, int columnsRow, AllowedPaths allowedPaths)
			throws FileNotFoundException {
		super(fileName, DEFAULT_DELIMITER, columnsRow, allowedPaths);
	}

	public InMemoryCSVDataSource(String fileName, String delimiter, int columnsRow, AllowedPaths allowedPaths)
			throws FileNotFoundException {
		super(fileName, delimiter, columnsRow, allowedPaths);
	}

	/**
	 * Reads every row of the file at once, as
	 * {@link io.github.adamw7.tools.data.source.interfaces.InMemoryDataSource} promises.
	 * The work is {@link #readAllRows()}, inherited from the file-source base class; this
	 * method is what publishes it, so {@code readAll()} stays off the surface of the
	 * forward-only {@link CSVDataSource} it extends.
	 */
	@Override
	public List<String[]> readAll() {
		return readAllRows();
	}

}
