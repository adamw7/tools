package io.github.adamw7.tools.data.source.file;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Paths;
import java.util.Scanner;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import io.github.adamw7.tools.data.compression.ZipUtils;
import io.github.adamw7.tools.data.source.interfaces.IterableDataSource;

public class CSVDataSource implements IterableDataSource {
	
	private final static Logger log = LogManager.getLogger(CSVDataSource.class.getName());
	
	public final static String DEFAULT_DELIMITER = ",";
	
	protected final String delimiter;
	protected final String regex;
	protected final int columnsRow;
	protected String[] columns;
	protected Scanner scanner;
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
		scanner = createScanner(fileName);
		this.delimiter = delimiter;
		this.columnsRow = columnsRow;
		this.fileName = fileName;
		regex = delimiter + "(?=([^\"]*\"[^\"]*\")*[^\"]*$)";
	}

	private Scanner createScanner(String fileName) throws FileNotFoundException {
		return new Scanner(ZipUtils.unzipIfNeeded(new FileInputStream(fileName), fileName), StandardCharsets.UTF_8);
	}
	
	@Override
	public String[] getColumnNames() {
		return columns;
	}

	@Override
	public void close() {
		scanner.close();
	}
	
	@Override
	public void open() {
		log.info("Opening: " + fileName);
		if (columnsRow != -1 && columns == null) {
			for (int i = 0; i < columnsRow; ++i) {
				columns = nextRow();
			}
		}
	}

	@Override
	public String[] nextRow() {
		if (scanner.hasNextLine()) {
			String line = scanner.nextLine();
			currentRow++;			
			if (line.trim().startsWith("#")) {
				return null;
			} else {
				return line.split(regex);				
			}
		}
		else {
			hasMoreData = false;
			return null;
		}
	}

	@Override
	public boolean hasMoreData() {
		return hasMoreData;
	}

	@Override
	public void reset() {
		try {
			scanner = createScanner(fileName);
		} catch (FileNotFoundException e) {
			throw new UncheckedIOException(e);
		}
		columns = null;
		hasMoreData = true;
		open();
	}
	
	public String getFileName () {
		return Paths.get(fileName).getFileName().toString();
	}
}
