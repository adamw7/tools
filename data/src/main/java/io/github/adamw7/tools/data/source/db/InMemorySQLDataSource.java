package io.github.adamw7.tools.data.source.db;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import io.github.adamw7.tools.data.source.interfaces.InMemoryDataSource;

public class InMemorySQLDataSource extends IterableSQLDataSource implements InMemoryDataSource {
	
	private final static Logger log = LogManager.getLogger(InMemorySQLDataSource.class.getName());

	
	public InMemorySQLDataSource(Connection connection, String query) {
		super(connection, query);
	}

	public InMemorySQLDataSource(Connection connection, String query, Object... params) {
		super(connection, query, params);
	}
	
	@Override
	public List<String[]> readAll() {
		try (PreparedStatement preparedStatement = connection.prepareStatement(query)) {
			bindParameters(preparedStatement, params);
			ResultSet resultSet = preparedStatement.executeQuery();
			List<String[]> allData = new ArrayList<>();
			while (resultSet.next()) {
				allData.add(getNextFrom(resultSet));
			}
			log.info("Loaded {} rows into memory", allData::size);
			return allData;
		} catch (SQLException e) {
			log.error(e);
			throw new UncheckedIOException(new IOException(e));
		}

	}

}
