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

public class InMemorySQLDataSource extends IterableSQLDataSource implements InMemoryDataSource {
	
	private final static Logger log = LogManager.getLogger(InMemorySQLDataSource.class.getName());

	
	public InMemorySQLDataSource(Connection connection, String query) {
		super(connection, query);
	}
	
	@Override
	public List<String[]> readAll() {
		try (Statement statement = connection.createStatement()) {
			ResultSet resultSet = statement.executeQuery(query);
			List<String[]> allData = new ArrayList<>();
			while (resultSet.next()) {
				allData.add(getNextFrom(resultSet));
			}
			log.info("Loaded " + allData.size() + " rows into memory");
			return allData;
		} catch (SQLException e) {
			log.warn(e);
			throw new UncheckedIOException(new IOException(e));
		}

	}

}
