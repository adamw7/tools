package io.github.adamw7.tools.data.source.file;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Paths;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import io.github.adamw7.tools.data.source.interfaces.IterableDataSource;

public class CSVDataSource extends AbstractFileSource {

	private static final String REGEX_SUFFIX = "(?=([^\"]*\"[^\"]*\")*[^\"]*$)";

	private final static Logger log = LogManager.getLogger(CSVDataSource.class.getName());

	public final static String DEFAULT_DELIMITER = ",";

	protected final String delimiter;
	protected final String regex;
	protected final int columnsRow;
	protected String[] columns;
	protected int currentRow = 0;
	protected boolean hasMoreData = true;

	public CSVDataSource(InputStream inputStream) throws FileNotFoundException {
		this(inputStream, DEFAULT_DELIMITER, -1);
	}

	public CSVDataSource(String fileName) throws FileNotFoundException {
		this(fileName, DEFAULT_DELIMITER, -1);
	}

	public CSVDataSource(String fileName, int columnsRow) throws FileNotFoundException {
		this(fileName, DEFAULT_DELIMITER, columnsRow);
	}
	
	public CSVDataSource(InputStream inoutStream, String delimiter, int columnsRow) throws FileNotFoundException {
		super(null);
		this.inputStream = inoutStream;
		scanner = createScanner(inputStream);
		this.delimiter = delimiter;
		this.columnsRow = columnsRow;
		regex = delimiter + REGEX_SUFFIX;
	}

	public CSVDataSource(String fileName, String delimiter, int columnsRow) throws FileNotFoundException {
		super(fileName);
		this.inputStream = new FileInputStream(fileName);
		this.fileName = fileName;
		scanner = createScanner(inputStream);
		this.delimiter = delimiter;
		this.columnsRow = columnsRow;
		regex = delimiter + REGEX_SUFFIX;
	}

	@Override
	public String[] getColumnNames() {
		return columns;
	}

	@Override
	public void open() {
		log.info("Opening: {}", fileName);
		if (columnsExist() && !columnsAreLoaded()) {
			loadColumns();
		}
	}

	private void loadColumns() {
		for (int i = 0; i < columnsRow; ++i) {
			columns = nextRow();
		}
	}

	private boolean columnsAreLoaded() {
		return columns != null;
	}

	private boolean columnsExist() {
		return columnsRow != -1;
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
		} else {
			hasMoreData = false;
			return null;
		}
	}

	@Override
	public void reset() {
		try {
			close();
			scanner = createScanner();
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
		columns = null;
		hasMoreData = true;
		open();
	}
	
	@Override
	public boolean hasMoreData() {
		return hasMoreData;
	}
}
