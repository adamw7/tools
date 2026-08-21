package io.github.adamw7.tools.data.source.db;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import io.github.adamw7.tools.data.source.interfaces.InMemoryDataSource;

/**
 * An {@link IterableSQLDataSource} that reads the whole result set into memory in one
 * call. The query is executed verbatim and unparameterised, on the same terms as the
 * superclass: see {@link IterableSQLDataSource} for what the caller must guarantee
 * about the SQL it hands over.
 */
public class InMemorySQLDataSource extends IterableSQLDataSource implements InMemoryDataSource {
	
	private static final Logger log = LogManager.getLogger(InMemorySQLDataSource.class.getName());

	
	/**
	 * @param connection the JDBC connection to run the query on
	 * @param query the SQL to execute, run verbatim &mdash; see {@link IterableSQLDataSource}
	 *        for what the caller must guarantee about how it was built
	 */
	public InMemorySQLDataSource(Connection connection, String query) {
		super(connection, query);
	}
	
	/**
	 * Executes the query given to the constructor, verbatim and unparameterised, and
	 * returns every row it produced.
	 */
	@Override
	public List<String[]> readAll() {
		try (Statement statement = connection.createStatement()) {
			ResultSet resultSet = statement.executeQuery(query);
			List<String[]> allData = new ArrayList<>();
			while (resultSet.next()) {
				allData.add(getNextFrom(resultSet));
			}
			log.info("Loaded {} rows into memory", allData::size);
			return allData;
		} catch (SQLException e) {
			log.error("Failed to load query result into memory: {}", query, e);
			throw new UncheckedIOException(new IOException(e));
		}

	}

}
