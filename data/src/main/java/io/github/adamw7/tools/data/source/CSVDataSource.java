package io.github.adamw7.tools.data.source;

import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import io.github.adamw7.tools.data.source.interfaces.IterableDataSource;

public class CSVDataSource implements IterableDataSource {
	
	private final static Logger log = LogManager.getLogger(CSVDataSource.class.getName());
	
	public final static String DEFAULT_DELIMITER = ",";
	
	protected final String delimiter;
	protected final String regex;
	protected final int columnsRow;
	protected String[] columns;
	protected BufferedReader bufferedReader;
	protected final String fileName; 
	protected int currentRow = 0;
	protected boolean hasMoreData = true;
	
	public CSVDataSource(String fileName) throws FileNotFoundException {
		this(fileName, DEFAULT_DELIMITER, -1);
	}
	
	public CSVDataSource(String fileName, int columnsRow) throws FileNotFoundException {
		this(fileName, DEFAULT_DELIMITER, columnsRow);
	}
	
	public CSVDataSource(String fileName, String delimiter, int columnsRow) throws FileNotFoundException {
		bufferedReader = new BufferedReader(new FileReader(fileName));
		this.delimiter = delimiter;
		this.columnsRow = columnsRow;
		this.fileName = fileName;
		regex = delimiter + "(?=([^\"]*\"[^\"]*\")*[^\"]*$)";
	}
	
	@Override
	public String[] getColumnNames() {
		return columns;
	}

	@Override
	public void close() throws Exception {
		bufferedReader.close();
	}
	
	@Override
	public void open() throws IOException {
		log.info("Opening: " + fileName);
		if (columnsRow != -1 && columns == null) {
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
				return line.split(regex);				
			}
		}
	}

	@Override
	public boolean hasMoreData() {
		return hasMoreData;
	}

	@Override
	public void reset() throws IOException {
		bufferedReader = new BufferedReader(new FileReader(fileName));
		columns = null;
		hasMoreData = true;
		open();
	}
}
