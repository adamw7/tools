package io.github.adamw7.tools.data.uniqueness;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import io.github.adamw7.tools.data.source.db.InMemoryParquetDataSource;
import io.github.adamw7.tools.data.source.db.IterableParquetDataSource;

/**
 * Regression tests for running the uniqueness checks against the Parquet
 * sources, which own their DuckDB connection. The subset search that follows a
 * successful check {@code reset()}s the source between passes; these used to
 * crash with a closed-connection error because the check closed the source
 * mid-search, and a connection-owning source cannot be reopened once closed.
 */
public class ParquetUniquenessCheckTest {

	private static Path parquetFile;

	@BeforeAll
	public static void createParquetFixture() throws Exception {
		parquetFile = Paths.get("target", "uniqueness-people.parquet").toAbsolutePath();
		Files.deleteIfExists(parquetFile);
		try (Connection connection = DriverManager.getConnection("jdbc:duckdb:");
				Statement statement = connection.createStatement()) {
			statement.execute("CREATE TABLE people (id INTEGER, name VARCHAR, city VARCHAR)");
			statement.execute("INSERT INTO people VALUES (1, 'Adam', 'A'), (2, 'Ewa', 'A'), (3, 'Jan', 'B')");
			statement.execute("COPY people TO '" + parquetFile + "' (FORMAT PARQUET)");
		}
	}

	@Test
	public void inMemoryUniqueCandidatesSurviveTheSubsetSearch() {
		InMemoryUniquenessCheck check = new InMemoryUniquenessCheck(
				new InMemoryParquetDataSource(parquetFile.toString()));

		Result result = check.exec("id", "name");

		assertTrue(result.isUnique());
		assertTrue(result.getBetterOptions().contains(new Result(true, new String[] { "id" })));
		assertTrue(result.getBetterOptions().contains(new Result(true, new String[] { "name" })));
	}

	@Test
	public void noMemoryUniqueCandidatesSurviveTheSubsetSearch() {
		NoMemoryUniquenessCheck check = new NoMemoryUniquenessCheck(
				new IterableParquetDataSource(parquetFile.toString()));

		Result result = check.exec("id", "name");

		assertTrue(result.isUnique());
		assertTrue(result.getBetterOptions().contains(new Result(true, new String[] { "id" })));
		assertTrue(result.getBetterOptions().contains(new Result(true, new String[] { "name" })));
	}

	@Test
	public void notUniqueColumnIsStillReported() {
		NoMemoryUniquenessCheck check = new NoMemoryUniquenessCheck(
				new IterableParquetDataSource(parquetFile.toString()));

		assertFalse(check.exec("city").isUnique());
	}
}
