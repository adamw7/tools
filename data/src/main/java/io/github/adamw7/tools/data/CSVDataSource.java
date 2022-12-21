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

	private boolean hasMoreData = true; 
	
	public CSVDataSource(String fileName, String delimiter) throws FileNotFoundException {
		bufferedReader = new BufferedReader(new FileReader(fileName));
		this.delimiter = delimiter;
	}
	
	public CSVDataSource(String fileName) throws FileNotFoundException {
		this(fileName, DEFAULT_DELIMITER);
	}

	@Override
	public void open() {
		
	}

	@Override
	public String[] nextRow() throws IOException {
		String line = bufferedReader.readLine();
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
