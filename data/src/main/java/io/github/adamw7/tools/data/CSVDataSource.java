package io.github.adamw7.tools.data;

import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;

import io.github.adamw7.tools.data.interfaces.DataSource;

public class CSVDataSource implements DataSource {
	public final static String DEFAULT_DELIMITER = ",";
	
	protected final BufferedReader bufferedReader;
	protected final String delimiter;
	protected final int columnsRow;
	protected int currentRow = 0;
	protected String[] columns;

	private boolean hasMoreData = true; 
	
	public CSVDataSource(String fileName, String delimiter, int columnsRow) throws FileNotFoundException {
		bufferedReader = new BufferedReader(new FileReader(fileName));
		this.delimiter = delimiter;
		this.columnsRow = columnsRow;
	}
	
	public CSVDataSource(String fileName) throws FileNotFoundException {
		this(fileName, DEFAULT_DELIMITER, -1);
	}
	
	public CSVDataSource(String fileName, int columnsRow) throws FileNotFoundException {
		this(fileName, DEFAULT_DELIMITER, columnsRow);
	}

	@Override
	public void open() throws IOException {
		if (columnsRow != -1) {
			for (int i = 0; i < columnsRow; ++i) {
				columns = nextRow();
			}
		}
	}

	@Override
	public String[] nextRow() throws IOException {
		String line = bufferedReader.readLine();
		currentRow++;
		if (line == null) {
			hasMoreData = false;
			return null;
		} else {
			if (line.trim().startsWith("#")) {
				return null;
			} else {
				return line.split(delimiter);				
			}
		}
	}

	@Override
	public void close() throws Exception {
		bufferedReader.close();
	}

	@Override
	public boolean hasMoreData() {
		return hasMoreData ;
	}
}
