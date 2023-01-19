package io.github.adamw7.tools.data.source.db;

import java.io.IOException;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

import io.github.adamw7.tools.data.source.interfaces.InMemoryDataSource;

public class InMemorySQLDataSource extends IterableSQLDataSource implements InMemoryDataSource {
	
	public InMemorySQLDataSource(Connection connection, String query) {
		super(connection, query);
	}
	
	@Override
	public List<String[]> readAll() throws IOException {
		Statement statement;
		try {
			statement = connection.createStatement();
			ResultSet resultSet = statement.executeQuery(query);
			List<String[]> allData = new ArrayList<>();
			while (resultSet.next()) {
				allData.add(getNextFrom(resultSet));
			}
			return allData;
		} catch (SQLException e) {
			e.printStackTrace();
			return new ArrayList<>();
		}

	}

}
