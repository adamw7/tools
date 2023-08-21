package io.github.adamw7.tools.data.source.file;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Paths;
import java.util.Scanner;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import io.github.adamw7.tools.data.compression.ZipUtils;
import io.github.adamw7.tools.data.source.interfaces.IterableDataSource;

public class CSVDataSource implements IterableDataSource {

	private static final String REGEX_SUFFIX = "(?=([^\"]*\"[^\"]*\")*[^\"]*$)";

	private final static Logger log = LogManager.getLogger(CSVDataSource.class.getName());

	public final static String DEFAULT_DELIMITER = ",";

	protected final String delimiter;
	protected final String regex;
	protected final int columnsRow;
	protected String[] columns;
	protected Scanner scanner;
	protected String fileName;
	protected int currentRow = 0;
	protected boolean hasMoreData = true;
	private InputStream inputStream;

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
		this.inputStream = inoutStream;
		scanner = createScanner(inputStream);
		this.delimiter = delimiter;
		this.columnsRow = columnsRow;
		regex = delimiter + REGEX_SUFFIX;
	}

	public CSVDataSource(String fileName, String delimiter, int columnsRow) throws FileNotFoundException {
		this.inputStream = new FileInputStream(fileName);
		this.fileName = fileName;
		scanner = createScanner(inputStream);
		this.delimiter = delimiter;
		this.columnsRow = columnsRow;
		regex = delimiter + REGEX_SUFFIX;
	}

	private Scanner createScanner(InputStream inputStream) {
		return new Scanner(ZipUtils.unzipIfNeeded(inputStream, fileName), StandardCharsets.UTF_8);
	}

	private Scanner createScanner(String fileName) throws FileNotFoundException {
		return createScanner(new FileInputStream(fileName));
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
	public boolean hasMoreData() {
		return hasMoreData;
	}

	@Override
	public void reset() {
		close();
		try {
			scanner = createScanner();
		} catch (FileNotFoundException e) {
			throw new UncheckedIOException(e);
		}
		columns = null;
		hasMoreData = true;
		open();
	}

	private Scanner createScanner() throws FileNotFoundException {
		if (fileName != null) {
			return createScanner(fileName);
		} else if (inputStream != null) {
			return createScanner(inputStream);
		} else {
			throw new IllegalStateException("Both input stream and file are nulls");
		}
	}

	public String getFileName() {
		return Paths.get(fileName).getFileName().toString();
	}
}
