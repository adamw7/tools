package io.github.adamw7.tools.data.source;

import java.io.IOException;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;

import io.github.adamw7.tools.data.source.interfaces.IterableDataSource;

public class IterableSQLDataSource implements IterableDataSource {

	private final String query;
	private final Connection connection;
	private ResultSet resultSet;
	private boolean hasMoreData = true;

	public IterableSQLDataSource(Connection connection, String query) {
		this.connection = connection;
		this.query = query;
	}

	@Override
	public void close() throws Exception {
		if (resultSet != null) {
			resultSet.close();
		}
	}

	@Override
	public String[] getColumnNames() {
		try {
			ResultSetMetaData meta = resultSet.getMetaData();
			String[] columns = new String[meta.getColumnCount()];
			for (int i = 0; i < columns.length; ++i) {
				columns[i] = meta.getColumnName(i + 1);
			}
			return columns;
		} catch (SQLException e) {
			throw new RuntimeException(e);
		}
	}

	@Override
	public void open() throws IOException {
		try {
			Statement statement = connection.createStatement();
			resultSet = statement.executeQuery(query);
		} catch (SQLException e) {
			throw new IOException(e);
		}
	}

	@Override
	public String[] nextRow() throws IOException {
		try {
			hasMoreData = resultSet.next();

			String[] row = new String[getColumnNames().length];
			for (int i = 0; i < getColumnNames().length; ++i) {
				row[i] = resultSet.getString(getColumnNames()[i]);
			}
			return row;
		} catch (SQLException e) {
			throw new IOException(e);
		}
	}

	@Override
	public boolean hasMoreData() {
		return hasMoreData;
	}

	@Override
	public void reset() throws IOException {
		hasMoreData = true;
		open();
	}

}
