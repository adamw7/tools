package io.github.adamw7.tools.data.source.db;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import io.github.adamw7.tools.data.source.interfaces.ColumnarDataSource;

/**
 * A {@link ColumnarDataSource} over a JDBC query, read a row at a time.
 *
 * <p><strong>The query is executed verbatim.</strong> This source hands the SQL it was
 * given to a plain {@link Statement}, binding nothing and rewriting nothing, so the
 * caller owns the statement's safety: assemble it from constants and values you
 * control, and never from input that arrived from outside the program. A query built
 * by concatenating untrusted text is a SQL injection here, exactly as it would be at
 * the {@code Statement} call this class makes on the caller's behalf.</p>
 *
 * <p>Running caller-supplied SQL is the contract, not an oversight, so SpotBugs'
 * {@code SQL_NONCONSTANT_STRING_PASSED_TO_EXECUTE} is excluded for the two methods
 * that execute it &mdash; see {@code data/spotbugs-exclude.xml}, which names those
 * methods alone so a genuine concatenation elsewhere in this package still fails.</p>
 */
public class IterableSQLDataSource implements ColumnarDataSource {

	private static final Logger log = LogManager.getLogger(IterableSQLDataSource.class.getName());

	private ResultSet resultSet;
	private Statement statement;
	protected boolean hasMoreData = true;
	protected final String query;
	protected final Connection connection;

	/**
	 * @param connection the JDBC connection to run the query on
	 * @param query the SQL to execute, run verbatim &mdash; see the class javadoc for what
	 *        the caller must guarantee about how it was built
	 */
	public IterableSQLDataSource(Connection connection, String query) {
		this.connection = connection;
		this.query = query;
	}

	@Override
	public void close() {
		closeQueryResources();
	}

	private void closeQueryResources() {
		try {
			Sql.running(this::closeOpenResources);
		} finally {
			resultSet = null;
			statement = null;
		}
	}

	private void closeOpenResources() throws SQLException {
		if (resultSet != null) {
			resultSet.close();
		}
		if (statement != null) {
			statement.close();
		}
	}

	@Override
	public String[] getColumnNames() {
		checkIfOpen();
		return Sql.answering(() -> getColumnsFrom(resultSet));
	}

	private void checkIfOpen() {
		if (resultSet == null) {
			throw new IllegalStateException("DataSource is not open");
		}
	}

	/**
	 * Executes the query given to the constructor, verbatim and unparameterised, and
	 * positions this source before its first row.
	 */
	@Override
	public void open() {
		closeQueryResources();
		Sql.running(this::executeQuery);
	}

	private void executeQuery() throws SQLException {
		statement = connection.createStatement();
		log.info("Executing query: {}", query);
		resultSet = statement.executeQuery(query);
	}

	@Override
	public String[] nextRow() {
		checkIfOpen();
		return Sql.answering(this::readRow);
	}

	private String[] readRow() throws SQLException {
		hasMoreData = resultSet.next();
		return hasMoreData ? getNextFrom(resultSet) : null;
	}

	@Override
	public boolean hasMoreData() {
		return hasMoreData;
	}

	@Override
	public List<String[]> nextRows(int batchSize) {
		applyFetchSize(batchSize);
		return ColumnarDataSource.super.nextRows(batchSize);
	}

	private void applyFetchSize(int batchSize) {
		if (batchSize <= 0) {
			return;
		}
		checkIfOpen();
		Sql.running(() -> resultSet.setFetchSize(batchSize));
	}

	/**
	 * Releases the query resources directly rather than through {@link #close()}, so a
	 * source that owns its connection — the Parquet ones, which close DuckDB in
	 * {@code close()} — is re-read on the connection it already has instead of having
	 * to override this method to say so.
	 */
	@Override
	public void reset() {
		closeQueryResources();
		hasMoreData = true;
		open();
	}

	protected static String[] getNextFrom(ResultSet resultSet) throws SQLException {
		return byColumn(resultSet.getMetaData().getColumnCount(), resultSet::getString);
	}

	protected static String[] getColumnsFrom(ResultSet resultSet) throws SQLException {
		ResultSetMetaData meta = resultSet.getMetaData();
		return byColumn(meta.getColumnCount(), meta::getColumnName);
	}

	/**
	 * Reads one value per column into an array, translating this class's zero-based
	 * indexing to JDBC's one-based. A row and a header differ only in which accessor
	 * supplies the value, so the walk lives here rather than in each of them.
	 */
	private static String[] byColumn(int columnCount, ColumnValue value) throws SQLException {
		String[] values = new String[columnCount];
		for (int i = 0; i < columnCount; ++i) {
			values[i] = value.at(i + 1);
		}
		return values;
	}

	/** A value read from a one-based JDBC column: a row's cell, or a column's name. */
	@FunctionalInterface
	private interface ColumnValue {
		String at(int column) throws SQLException;
	}

}
