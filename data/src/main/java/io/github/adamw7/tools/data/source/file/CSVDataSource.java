package io.github.adamw7.tools.data.source.file;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.regex.Pattern;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import io.github.adamw7.tools.data.source.interfaces.ColumnarDataSource;

public class CSVDataSource extends AbstractFileSource implements ColumnarDataSource {

	private static final String REGEX_SUFFIX = "(?=([^\"]*\"[^\"]*\")*[^\"]*$)";

	private static final Logger log = LogManager.getLogger(CSVDataSource.class.getName());

	public final static String DEFAULT_DELIMITER = ",";

	protected final String delimiter;
	protected final String regex;
	protected final int columnsRow;
	protected String[] columns;
	protected boolean hasMoreData = true;

	public CSVDataSource(InputStream inputStream) {
		this(inputStream, DEFAULT_DELIMITER, -1);
	}

	public CSVDataSource(String fileName) throws FileNotFoundException {
		this(fileName, DEFAULT_DELIMITER, -1);
	}

	public CSVDataSource(String fileName, int columnsRow) throws FileNotFoundException {
		this(fileName, DEFAULT_DELIMITER, columnsRow);
	}
	
	public CSVDataSource(InputStream inputStream, String delimiter, int columnsRow) {
		super(inputStream);
		this.inputStream = inputStream;
		scanner = createScanner(inputStream);
		this.delimiter = delimiter;
		this.columnsRow = columnsRow;
		regex = Pattern.quote(delimiter) + REGEX_SUFFIX;
	}

	public CSVDataSource(String fileName, String delimiter, int columnsRow) throws FileNotFoundException {
		super(fileName);
		this.delimiter = delimiter;
		this.columnsRow = columnsRow;
		regex = Pattern.quote(delimiter) + REGEX_SUFFIX;
	}

	public CSVDataSource(String fileName, AllowedPaths allowedPaths) throws FileNotFoundException {
		this(fileName, DEFAULT_DELIMITER, -1, allowedPaths);
	}

	public CSVDataSource(String fileName, int columnsRow, AllowedPaths allowedPaths) throws FileNotFoundException {
		this(fileName, DEFAULT_DELIMITER, columnsRow, allowedPaths);
	}

	/**
	 * Reads {@code fileName} confined to {@code allowedPaths}, rather than to whatever
	 * base directory the process happens to have set.
	 */
	public CSVDataSource(String fileName, String delimiter, int columnsRow, AllowedPaths allowedPaths)
			throws FileNotFoundException {
		super(fileName, allowedPaths);
		this.delimiter = delimiter;
		this.columnsRow = columnsRow;
		regex = Pattern.quote(delimiter) + REGEX_SUFFIX;
	}

	/**
	 * The header row read by {@link #open()}. A source built without one &mdash;
	 * {@code new CSVDataSource(fileName)} and the other constructors defaulting
	 * {@code columnsRow} to {@code -1} &mdash; has no column names to report, so it says
	 * that here rather than handing back a {@code null} array the caller only finds out
	 * about further downstream: {@code execForAllColumns()} on such a source used to
	 * fail with {@code Wrong input: null}, which names neither the source nor the
	 * missing header.
	 *
	 * <p>The header outlives the read: a source closed after a full pass still names its
	 * columns, since {@link #close()} ends the scan without unlearning the schema. Only
	 * {@link #reset()} drops them, and it reloads them as it reopens.</p>
	 */
	@Override
	public String[] getColumnNames() {
		if (!columnsExist()) {
			throw new IllegalStateException(
					"Source was created without a header row, so it has no column names; "
							+ "pass the 1-based row the header sits on as columnsRow");
		}
		if (columnsAreLoaded()) {
			return columns;
		}
		if (!opened) {
			throw new IllegalStateException("DataSource is not open");
		}
		throw new IllegalStateException(
				"Row " + columnsRow + " holds no header: the file ends before it, or it is a comment");
	}

	@Override
	public void open() {
		log.info("Opening: {}", fileName);
		opened = true;
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
			if (line.trim().startsWith("#")) {
				return null;
			} else {
				// The -1 limit keeps trailing empty columns, so every row of a
				// well-formed file has the same arity as its header.
				return line.split(regex, -1);
			}
		} else {
			hasMoreData = false;
			return null;
		}
	}

	@Override
	public void reset() {
		if (fileName == null) {
			throw new IllegalStateException("Cannot reset a source backed by a raw input stream");
		}
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
