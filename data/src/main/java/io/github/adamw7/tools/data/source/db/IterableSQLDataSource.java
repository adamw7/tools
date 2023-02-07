package io.github.adamw7.tools.data.source.db;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import io.github.adamw7.tools.data.source.interfaces.IterableDataSource;

public class IterableSQLDataSource implements IterableDataSource {

	private final static Logger log = LogManager.getLogger(IterableSQLDataSource.class.getName());

	private ResultSet resultSet;
	protected boolean hasMoreData = true;
	protected final String query;
	protected final Connection connection;

	public IterableSQLDataSource(Connection connection, String query) {
		this.connection = connection;
		this.query = query;
	}

	@Override
	public void close() {
		if (resultSet != null) {
			try {
				resultSet.close();
			} catch (SQLException e) {
				throw new UncheckedIOException(new IOException(e));
			}
		}
	}

	@Override
	public String[] getColumnNames() {
		try {
			return getColumnsFrom(resultSet);
		} catch (SQLException e) {
			throw new RuntimeException(e);
		}
	}

	@Override
	public void open() {
		try {
			Statement statement = connection.createStatement();
			log.info("Executing query: " + query);
			resultSet = statement.executeQuery(query);
		} catch (SQLException e) {
			throw new UncheckedIOException(new IOException(e));
		}
	}

	@Override
	public String[] nextRow() {
		try {
			hasMoreData = resultSet.next();
			if (hasMoreData) {
				return getNextFrom(resultSet);
			} else {
				return null;
			}
		} catch (SQLException e) {
			throw new UncheckedIOException(new IOException(e));
		}
	}

	@Override
	public boolean hasMoreData() {
		return hasMoreData;
	}

	@Override
	public void reset() {
		close();
		hasMoreData = true;
		open();
	}

	protected static String[] getNextFrom(ResultSet resultSet) throws SQLException {
		String[] columns = getColumnsFrom(resultSet);
		String[] row = new String[columns.length];
		for (int i = 0; i < columns.length; ++i) {
			row[i] = resultSet.getString(columns[i]);
		}
		return row;
	}

	protected static String[] getColumnsFrom(ResultSet resultSet) throws SQLException {
		ResultSetMetaData meta = resultSet.getMetaData();
		String[] columns = new String[meta.getColumnCount()];
		for (int i = 0; i < columns.length; ++i) {
			columns[i] = meta.getColumnName(i + 1);
		}
		return columns;
	}

}
