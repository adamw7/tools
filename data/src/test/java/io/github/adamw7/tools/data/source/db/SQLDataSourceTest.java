package io.github.adamw7.tools.data.source.db;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.fail;

import java.io.IOException;
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
		return Stream.of(Arguments.of(Utils.named(iterableSource)), 
				Arguments.of(Utils.named(inMemorySource)));
	}
	
	static Stream<Arguments> wrongQuerySources() {
		String query = "SELECT * FROM NON_EXISTING_TABLE";
		IterableSQLDataSource iterableSource = new IterableSQLDataSource(connection, query);
		InMemorySQLDataSource inMemorySource = new InMemorySQLDataSource(connection, query);
		return Stream.of(Arguments.of(Utils.named(iterableSource)), Arguments.of(Utils.named(inMemorySource)));
	}

	@ParameterizedTest
	@MethodSource("happyPathSources")
	public void happyPath(IterableSQLDataSource source) {
		try {
			source.open();

			assertEquals("ID", source.getColumnNames()[0]);
			assertEquals("NAME", source.getColumnNames()[1]);
			assertEquals("SURNAME", source.getColumnNames()[2]);

			String[] row = source.nextRow();
			assertEquals("1", row[0]);
			assertEquals("Adam", row[1]);
			assertEquals("W", row[2]);
			source.close();
		} catch (Exception e) {
			fail(e.getMessage());
		}
	}

	@ParameterizedTest
	@MethodSource("wrongQuerySources")
	public void wrongQuery(IterableSQLDataSource source) throws Exception {
		IOException thrown = assertThrows(IOException.class, () -> {
			source.open();
		}, "Expected open method to throw, but it didn't");

		assertEquals("java.sql.SQLSyntaxErrorException: Table/View 'NON_EXISTING_TABLE' does not exist.", thrown.getMessage());
	}
}