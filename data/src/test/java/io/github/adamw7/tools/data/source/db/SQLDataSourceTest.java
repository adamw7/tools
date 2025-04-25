package io.github.adamw7.tools.data.source.db;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.params.provider.Arguments.of;


import java.io.UncheckedIOException;
import java.util.stream.Stream;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import io.github.adamw7.tools.data.DBTest;
import io.github.adamw7.tools.data.Utils;

public class SQLDataSourceTest extends DBTest {

	static Stream<Arguments> happyPathSources() {
		String query = "SELECT * FROM PEOPLE";
		IterableSQLDataSource iterableSource = new IterableSQLDataSource(connection, query);
		InMemorySQLDataSource inMemorySource = new InMemorySQLDataSource(connection, query);
		return Stream.of(of(Utils.named(iterableSource)), of(Utils.named(inMemorySource)));
	}

	static Stream<Arguments> happyPathMultiTableSources() {
		String query = "SELECT * FROM SALARY INNER JOIN PEOPLE ON PEOPLE.ID = SALARY.ID";
		IterableSQLDataSource iterableSource = new IterableSQLDataSource(connection, query);
		InMemorySQLDataSource inMemorySource = new InMemorySQLDataSource(connection, query);
		return Stream.of(of(Utils.named(iterableSource)), of(Utils.named(inMemorySource)));
	}

	static Stream<Arguments> wrongQuerySources() {
		String query = "SELECT * FROM NON_EXISTING_TABLE";
		IterableSQLDataSource iterableSource = new IterableSQLDataSource(connection, query);
		InMemorySQLDataSource inMemorySource = new InMemorySQLDataSource(connection, query);
		return Stream.of(of(Utils.named(iterableSource)), of(Utils.named(inMemorySource)));
	}

	@ParameterizedTest
	@MethodSource("happyPathSources")
	public void happyPath(IterableSQLDataSource source) {
		source.open();

		assertEquals("ID", source.getColumnNames()[0]);
		assertEquals("NAME", source.getColumnNames()[1]);
		assertEquals("SURNAME", source.getColumnNames()[2]);

		String[] row = source.nextRow();
		assertEquals("1", row[0]);
		assertEquals("Adam", row[1]);
		assertEquals("W", row[2]);
		Utils.close(source);
	}

	@ParameterizedTest
	@MethodSource("happyPathMultiTableSources")
	public void happyPathMultiTable(IterableSQLDataSource source) {
		source.open();

		assertEquals("ID", source.getColumnNames()[0]);
		assertEquals("VALUE", source.getColumnNames()[1]);

		String[] row = source.nextRow();
		assertEquals("1", row[0]);
		assertEquals("1000", row[1]);
		Utils.close(source);
	}

	@ParameterizedTest
	@MethodSource("wrongQuerySources")
	public void wrongQuery(IterableSQLDataSource source) {
		UncheckedIOException thrown = assertThrows(UncheckedIOException.class, source::open, "Expected open method to throw, but it didn't");

		assertEquals("java.io.IOException: java.sql.SQLSyntaxErrorException: 42X05 : [0] NON_EXISTING_TABLE",
				thrown.getMessage());
	}
}
