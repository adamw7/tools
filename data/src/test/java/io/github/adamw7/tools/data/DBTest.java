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
	
	protected final static String URL_PREFIX = "jdbc:derby:memory:testDB;";

	@BeforeAll
	public static void setup() throws Exception {
		String connectionURL = URL_PREFIX + "create=true";
		DriverManager.registerDriver(new org.apache.derby.jdbc.EmbeddedDriver());
		connection = DriverManager.getConnection(connectionURL);
		prepareData();		
	}

	private static void prepareData() throws Exception {
		createPeopleTable();
		insertPeopleData();
		createSalaryTable();
		insertSalaryData();
		query = insertDataToDBFromCSV(Utils.createInMemoryDataSource(Utils.getHouseholdFile(), 1));
	}

	private static void insertSalaryData() throws SQLException {
		executeUpdate(SQLCreator.insert("SALARY", new String[] {"1", "1000"}));
	}

	private static void createSalaryTable() throws SQLException {
		executeUpdate(SQLCreator.table("SALARY", new String[] {"ID", "Value"}));
	}

	@AfterAll
	public static void tearDown() throws SQLException {
		connection.close();
		try {
			DriverManager.getConnection(URL_PREFIX + "drop=true");
		} catch (Exception ignored) {
			
		}
	}
	
	private static void insertPeopleData() throws SQLException {
		executeUpdate(SQLCreator.insert("PEOPLE", new String[] {"1", "Adam", "W"}));
	}

	private static void createPeopleTable() throws SQLException {
		executeUpdate(SQLCreator.table("PEOPLE", new String[] {"ID", "Name", "Surname"}));
	}
	
	protected static String insertDataToDBFromCSV(InMemoryCSVDataSource inMemoryDataSource) throws Exception {
		inMemoryDataSource.open();
		try (inMemoryDataSource) {
			String fileName = inMemoryDataSource.getFileName();
			String[] columnNames = inMemoryDataSource.getColumnNames();
			createTable(fileName, columnNames);
			insertDataToDB(fileName, inMemoryDataSource.readAll());
			return SQLCreator.createSelectQueryBasedOn(fileName, columnNames);
		}
	}

	private static void insertDataToDB(String tableName, List<String[]> all) throws SQLException {
		Statement statement = connection.createStatement();
		for (String[] row : all) {
			if (row != null) {
				String sql = SQLCreator.insert(tableName, row);
				statement.executeUpdate(sql);
			}
		}
		connection.commit();
	}

	private static void createTable(String table, String[] columnNames) throws SQLException {
		String sql = SQLCreator.table(table, columnNames);
		executeUpdate(sql);
	}

	private static void executeUpdate(String sql) throws SQLException {
		Statement statement = connection.createStatement();
		statement.executeUpdate(sql);
		statement.close();
	}
}
