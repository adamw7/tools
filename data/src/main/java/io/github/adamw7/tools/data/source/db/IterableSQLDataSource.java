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
	private int columnCount;
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
			columnCount = 0;
		}
	}

	/**
	 * Closes both resources whichever of them fails. A {@link ResultSet#close()} that
	 * throws must not skip the statement: {@link #closeQueryResources()} nulls both
	 * fields either way, so a statement left open here — and the server-side cursor it
	 * holds on a connection the caller keeps using — is unreachable and can never be
	 * closed. Both failures are reported as one, the statement's suppressed by the
	 * result set's.
	 */
	private void closeOpenResources() throws SQLException {
		SQLException resultSetFailure = closeResultSet();
		SQLException statementFailure = closeStatement();
		SQLException failure = firstOf(resultSetFailure, statementFailure);
		if (failure != null) {
			throw failure;
		}
	}

	private SQLException closeResultSet() {
		return resultSet == null ? null : failureFrom(resultSet::close);
	}

	private SQLException closeStatement() {
		return statement == null ? null : failureFrom(statement::close);
	}

	/** @return what {@code call} failed with, or {@code null} if it succeeded */
	private static SQLException failureFrom(Sql.Call call) {
		try {
			call.run();
			return null;
		} catch (SQLException e) {
			return e;
		}
	}

	/**
	 * @return {@code first}, carrying {@code second} as a suppressed exception, or
	 *         whichever of the two is not {@code null}
	 */
	private static SQLException firstOf(SQLException first, SQLException second) {
		if (first == null) {
			return second;
		}
		if (second != null) {
			first.addSuppressed(second);
		}
		return first;
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
		columnCount = columnCountOf(resultSet);
	}

	@Override
	public String[] nextRow() {
		checkIfOpen();
		return Sql.answering(this::readRow);
	}

	/**
	 * Advances the result set and reads the row it landed on, or {@code null} once it is
	 * past the last one &mdash; the end-of-data answer
	 * {@link io.github.adamw7.tools.data.source.interfaces.IterableDataSource#nextRow()}
	 * defines, and the same value {@link #hasMoreData()} starts reporting from here. A
	 * zero-length array is not free to mean it: a query selecting no columns produces
	 * rows of exactly that shape.
	 */
	private String[] readRow() throws SQLException {
		hasMoreData = resultSet.next();
		return hasMoreData ? getNextFrom(resultSet, columnCount) : null;
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

	/**
	 * Reads one row, taking the column count the caller read once at the start of the
	 * result set rather than asking for the metadata again — a round trip to the server
	 * on some drivers, and the row count is what it would be multiplied by.
	 */
	protected static String[] getNextFrom(ResultSet resultSet, int columnCount) throws SQLException {
		return byColumn(columnCount, resultSet::getString);
	}

	protected static int columnCountOf(ResultSet resultSet) throws SQLException {
		return resultSet.getMetaData().getColumnCount();
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
