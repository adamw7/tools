package io.github.adamw7.tools.data;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;

import io.github.adamw7.tools.data.source.file.InMemoryCSVDataSource;

public class DBTest {
	protected static Connection connection;
	protected static String query;

	@BeforeAll
	public static void setup() throws Exception {
		String connectionURL = "jdbc:derby:memory:testDB;create=true";
		DriverManager.registerDriver(new org.apache.derby.jdbc.EmbeddedDriver());
		connection = DriverManager.getConnection(connectionURL);
		createTables();
		insertPlaceholderData();
		query = insertDataToDBFromCSV(Utils.createInMemoryDataSource(Utils.getHouseholdFile(), 1));		
	}

	@AfterAll
	public static void tearDown() throws SQLException {
		connection.close();
		try {
			DriverManager.getConnection("jdbc:derby:memory:testDB;drop=true");
		} catch (Exception ignored) {
			
		}
	}
	
	private static void insertPlaceholderData() throws SQLException {
		Statement statement = connection.createStatement();
		statement.executeUpdate("insert into people(ID,NAME,SURNAME) values(1,'Adam','W')");
		connection.commit();
		statement.close();
	}

	private static void createTables() throws SQLException {
		Statement statement = connection.createStatement();
		statement.executeUpdate(createTableSQL());
		statement.close();
	}
	
	private static String createTableSQL() {
		return "CREATE TABLE PEOPLE " + "(id INTEGER not NULL, " + " Name VARCHAR(255), " + " Surname VARCHAR(255),"
				+ "PRIMARY KEY ( id ))";
	}
	
	protected static String insertDataToDBFromCSV(InMemoryCSVDataSource inMemoryDataSource) throws Exception {
		inMemoryDataSource.open();
		createTable(inMemoryDataSource.getColumnNames());
		insertDataToDB(inMemoryDataSource.readAll());
		return createSelectQueryBasedOn("FROM_CSV", inMemoryDataSource.getColumnNames());
	}

	private static String createSelectQueryBasedOn(String table, String[] columnNames) {
		StringBuilder sql = new StringBuilder("SELECT ");
		
		for (String column : columnNames) {
			sql.append(column);
			sql.append(", ");
		}
		sql.delete(sql.length() - 2, sql.length() - 1);
		sql.append("FROM ");
		sql.append(table);
		return sql.toString();
	}

	private static void insertDataToDB(List<String[]> all) throws SQLException {
		Statement statement = connection.createStatement();
		for (String[] row : all) {
			if (row != null) {
				String sql = createInsertSQL(row);
				statement.executeUpdate(sql);
			}
		}
		connection.commit();
	}

	private static String createInsertSQL(String[] row) {		
		StringBuilder insert = new StringBuilder("INSERT INTO FROM_CSV VALUES (");
		for (String value : row) {
			insert.append("'");
			insert.append(value);
			insert.append("',");			
		}
		insert.delete(insert.length() - 1, insert.length()); 
		insert.append(")");
		return insert.toString();
	}

	private static void createTable(String[] columnNames) throws SQLException {
		String sql = createTableSQL(columnNames);
		Statement statement = connection.createStatement();
		statement.executeUpdate(sql);
		statement.close();
	}

	private static String createTableSQL(String[] columnNames) {
		StringBuilder sql = new StringBuilder("CREATE TABLE FROM_CSV (");
		for (String column : columnNames) {
			sql.append(column);
			sql.append(" VARCHAR(255),");
		}
		sql.deleteCharAt(sql.length() - 1);
		sql.append(")");
		return sql.toString();
	}
}
