package io.github.adamw7.tools.data.source.db;

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
		return Sql.answering(() -> DriverManager.getConnection(IN_MEMORY_URL));
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
		Sql.running(() -> closeIfOpen(connection));
	}

	private static void closeIfOpen(Connection connection) throws SQLException {
		if (connection != null && !connection.isClosed()) {
			connection.close();
		}
	}
}
