package io.github.adamw7.tools.data.source.db;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.params.provider.Arguments.of;

import java.io.UncheckedIOException;
import java.lang.reflect.Proxy;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import io.github.adamw7.tools.data.Utils;
import io.github.adamw7.tools.data.source.interfaces.ColumnarDataSource;

public class ParquetDataSourceTest {

	private static Path parquetFile;

	@BeforeAll
	public static void createParquetFixture() throws Exception {
		parquetFile = Paths.get("target", "people.parquet").toAbsolutePath();
		Files.deleteIfExists(parquetFile);
		try (Connection connection = DriverManager.getConnection("jdbc:duckdb:");
				Statement statement = connection.createStatement()) {
			statement.execute("CREATE TABLE people (id INTEGER, name VARCHAR, surname VARCHAR)");
			statement.execute("INSERT INTO people VALUES (1, 'Adam', 'W'), (2, 'Ewa', 'K'), (3, 'Jan', 'N')");
			statement.execute("COPY people TO '" + parquetFile + "' (FORMAT PARQUET)");
		}
	}

	static Stream<Arguments> happyPathSources() {
		IterableParquetDataSource iterable = new IterableParquetDataSource(parquetFile.toString());
		InMemoryParquetDataSource inMemory = new InMemoryParquetDataSource(parquetFile.toString());
		return Stream.of(of(Utils.named(iterable)), of(Utils.named(inMemory)));
	}

	@ParameterizedTest
	@MethodSource("happyPathSources")
	public void happyPath(ColumnarDataSource source) {
		source.open();

		String[] columnNames = source.getColumnNames();
		assertEquals("id", columnNames[0]);
		assertEquals("name", columnNames[1]);
		assertEquals("surname", columnNames[2]);

		String[] row = source.nextRow();
		assertEquals("1", row[0]);
		assertEquals("Adam", row[1]);
		assertEquals("W", row[2]);

		int rows = 1;
		while (source.hasMoreData()) {
			String[] next = source.nextRow();
			if (next != null) {
				++rows;
			}
		}
		Utils.close(source);
		assertEquals(3, rows);
	}

	@Test
	public void inMemoryReadAll() {
		InMemoryParquetDataSource source = new InMemoryParquetDataSource(parquetFile.toString());
		List<String[]> all = source.readAll();
		Utils.close(source);
		assertEquals(3, all.size());
		assertEquals("Adam", all.get(0)[1]);
	}

	@Test
	public void nextRowsLoadsRequestedBatch() {
		IterableParquetDataSource source = new IterableParquetDataSource(parquetFile.toString());
		source.open();

		List<String[]> firstBatch = source.nextRows(2);
		assertEquals(2, firstBatch.size());

		List<String[]> rest = source.nextRows(1000);
		assertEquals(1, rest.size());

		assertTrue(source.nextRows(10).isEmpty());
		Utils.close(source);
	}

	@Test
	public void resetReopensSourceAfterExhaustion() {
		IterableParquetDataSource source = new IterableParquetDataSource(parquetFile.toString());
		source.open();
		int firstPass = drain(source);
		source.reset();
		int secondPass = drain(source);
		Utils.close(source);
		// reset() releases the query resources but keeps the owned DuckDB connection, so the
		// same rows are produced again instead of an empty (or failed) read.
		assertEquals(3, firstPass);
		assertEquals(firstPass, secondPass);
	}

	@Test
	public void closeDisposesOwnedConnection() {
		IterableParquetDataSource source = new IterableParquetDataSource(parquetFile.toString());
		source.open();
		Utils.close(source);
		// The source owns its DuckDB connection, so once closed a fresh read must fail fast
		// rather than silently querying a disposed connection.
		assertThrows(UncheckedIOException.class, source::open);
	}

	@Test
	public void missingFileFailsOnOpen() {
		String missing = Paths.get("target", "does-not-exist.parquet").toAbsolutePath().toString();
		IterableParquetDataSource source = new IterableParquetDataSource(missing);
		assertThrows(UncheckedIOException.class, source::open);
		Utils.close(source);
	}

	@Test
	public void nullPathIsRejected() {
		assertThrows(IllegalArgumentException.class, () -> new IterableParquetDataSource(null));
		assertThrows(IllegalArgumentException.class, () -> new InMemoryParquetDataSource("  "));
	}

	@Test
	public void parquetSourcesAreColumnar() {
		assertTrue(ColumnarDataSource.class.isAssignableFrom(IterableParquetDataSource.class));
		assertTrue(ColumnarDataSource.class.isAssignableFrom(InMemoryParquetDataSource.class));
		assertFalse(IterableParquetDataSource.class.isInterface());
	}

	@Test
	public void repeatedOpenDoesNotLeakPreviousStatement() throws Exception {
		List<Statement> created = new ArrayList<>();
		try (Connection real = DriverManager.getConnection("jdbc:duckdb:")) {
			Connection recording = recordingConnection(real, created);
			IterableSQLDataSource source = new IterableSQLDataSource(recording,
					DuckDbParquet.readQuery(parquetFile.toString()));
			source.open();
			source.open();
			// The second open() must release the first statement instead of leaking it on the
			// (owned) connection until it is disposed.
			assertEquals(2, created.size());
			assertTrue(created.get(0).isClosed(), "first statement leaked on repeated open()");
			source.close();
			assertTrue(created.get(1).isClosed(), "second statement leaked on close()");
		}
	}

	@Test
	public void repeatedOpenReReadsFromStart() {
		IterableParquetDataSource source = new IterableParquetDataSource(parquetFile.toString());
		source.open();
		assertEquals("1", source.nextRow()[0]);
		source.open();
		// Re-opening starts a fresh read rather than continuing the exhausted one.
		assertEquals("1", source.nextRow()[0]);
		Utils.close(source);
	}

	private static Connection recordingConnection(Connection real, List<Statement> created) {
		return (Connection) Proxy.newProxyInstance(Connection.class.getClassLoader(),
				new Class<?>[] { Connection.class }, (proxy, method, args) -> {
					Object result = method.invoke(real, args);
					if (method.getName().equals("createStatement")) {
						created.add((Statement) result);
					}
					return result;
				});
	}

	private static int drain(IterableParquetDataSource source) {
		int rows = 0;
		while (source.hasMoreData()) {
			String[] row = source.nextRow();
			if (row != null) {
				++rows;
			}
		}
		return rows;
	}
}
