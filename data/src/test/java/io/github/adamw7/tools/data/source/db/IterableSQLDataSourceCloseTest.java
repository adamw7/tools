package io.github.adamw7.tools.data.source.db;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.io.UncheckedIOException;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;

import org.junit.jupiter.api.Test;

/**
 * What {@link IterableSQLDataSource} does when JDBC fails on the way out, which the
 * embedded-Derby tests cannot reach: the driver has to refuse to close. The source
 * forgets both resources once it has closed them, so a statement it skipped is
 * unreachable — and on a connection the caller keeps using, leaked.
 */
public class IterableSQLDataSourceCloseTest {

	private static final String QUERY = "SELECT * FROM PEOPLE";

	private final Connection connection = mock(Connection.class);
	private final Statement statement = mock(Statement.class);
	private final ResultSet resultSet = mock(ResultSet.class);

	private IterableSQLDataSource openedSource() throws SQLException {
		ResultSetMetaData meta = mock(ResultSetMetaData.class);
		when(meta.getColumnCount()).thenReturn(2);
		when(resultSet.getMetaData()).thenReturn(meta);
		when(statement.executeQuery(QUERY)).thenReturn(resultSet);
		when(connection.createStatement()).thenReturn(statement);
		IterableSQLDataSource source = new IterableSQLDataSource(connection, QUERY);
		source.open();
		return source;
	}

	@Test
	public void statementIsClosedWhenTheResultSetRefusesTo() throws SQLException {
		IterableSQLDataSource source = openedSource();
		doThrow(new SQLException("connection dropped")).when(resultSet).close();

		assertThrows(UncheckedIOException.class, source::close);

		verify(statement).close();
	}

	@Test
	public void resettingClosesTheStatementWhenTheResultSetRefusesTo() throws SQLException {
		IterableSQLDataSource source = openedSource();
		doThrow(new SQLException("connection dropped")).when(resultSet).close();

		assertThrows(UncheckedIOException.class, source::reset);

		verify(statement).close();
	}

	@Test
	public void bothCloseFailuresAreReportedAsOne() throws SQLException {
		IterableSQLDataSource source = openedSource();
		SQLException resultSetFailure = new SQLException("result set");
		SQLException statementFailure = new SQLException("statement");
		doThrow(resultSetFailure).when(resultSet).close();
		doThrow(statementFailure).when(statement).close();

		UncheckedIOException thrown = assertThrows(UncheckedIOException.class, source::close);

		Throwable reported = thrown.getCause().getCause();
		assertSame(resultSetFailure, reported);
		assertArrayEquals(new Throwable[] { statementFailure }, reported.getSuppressed());
	}

	@Test
	public void aStatementThatRefusesToCloseIsReportedOnItsOwn() throws SQLException {
		IterableSQLDataSource source = openedSource();
		SQLException statementFailure = new SQLException("statement");
		doThrow(statementFailure).when(statement).close();

		UncheckedIOException thrown = assertThrows(UncheckedIOException.class, source::close);

		assertSame(statementFailure, thrown.getCause().getCause());
	}

	@Test
	public void closingASourceThatWasNeverOpenedTouchesNothing() {
		IterableSQLDataSource source = new IterableSQLDataSource(connection, QUERY);

		assertDoesNotThrow(source::close);

		verifyNoInteractions(connection, statement, resultSet);
	}

	@Test
	public void theColumnCountIsReadOnceForTheWholeResultSet() throws SQLException {
		IterableSQLDataSource source = openedSource();
		when(resultSet.next()).thenReturn(true, true, false);
		when(resultSet.getString(anyInt())).thenReturn("value");

		source.nextRow();
		source.nextRow();
		source.nextRow();

		// Once at open(), and never again per row: some drivers make getMetaData() a
		// round trip, which a row-at-a-time read would multiply by the row count.
		verify(resultSet, times(1)).getMetaData();
	}
}
