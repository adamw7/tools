package io.github.adamw7.tools.data.source.db;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

/**
 * Builds the in-process DuckDB plumbing that lets the Parquet data sources reuse the JDBC
 * {@code source.db} machinery: an in-memory DuckDB connection and a {@code read_parquet} query
 * over a given file. Keeping this here means both the iterable and in-memory Parquet sources
 * share one definition of how a Parquet file is turned into a JDBC result set.
 */
final class DuckDbParquet {

	private static final String IN_MEMORY_URL = "jdbc:duckdb:";

	private DuckDbParquet() {
	}

	static Connection connect() {
		try {
			return DriverManager.getConnection(IN_MEMORY_URL);
		} catch (SQLException e) {
			throw new UncheckedIOException(new IOException(e));
		}
	}

	static String readQuery(String filePath) {
		if (filePath == null || filePath.trim().isEmpty()) {
			throw new IllegalArgumentException("Parquet file path must not be null or empty");
		}
		return "SELECT * FROM read_parquet('" + escape(filePath) + "')";
	}

	private static String escape(String filePath) {
		return filePath.replace("'", "''");
	}

	static void close(Connection connection) {
		try {
			if (connection != null && !connection.isClosed()) {
				connection.close();
			}
		} catch (SQLException e) {
			throw new UncheckedIOException(new IOException(e));
		}
	}
}
