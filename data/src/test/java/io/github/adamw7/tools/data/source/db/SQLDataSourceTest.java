package io.github.adamw7.tools.data.source.db;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.fail;

import java.io.IOException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

public class SQLDataSourceTest {
	private static Connection connection;
	
	@BeforeAll
	public static void setup() throws Exception {
	    String connectionURL = "jdbc:derby:memory:testDB;create=true";
	    DriverManager.registerDriver(new org.apache.derby.jdbc.EmbeddedDriver());
	    connection = DriverManager.getConnection(connectionURL);
	    createTables();
	    insertData();
	}
	
	@AfterAll
	public static void tearDown() throws SQLException {
		connection.close();
	}
	
	private static void insertData() throws SQLException {
		Statement statement = connection.createStatement();
		statement.executeUpdate("insert into people(ID,NAME,SURNAME) values(1,'Adam','W')");
		connection.commit();
	}

	private static void createTables() throws SQLException {
		Statement statement = connection.createStatement();
		statement.executeUpdate(createTableSQL());
	}

	private static String createTableSQL() {
		return "CREATE TABLE PEOPLE " +
                "(id INTEGER not NULL, " +
                " Name VARCHAR(255), " + 
                " Surname VARCHAR(255),"
                + "PRIMARY KEY ( id ))";
	}
	
	@Test
	public void happyPath() {
		try {
			String query = "SELECT * FROM PEOPLE";
			IterableSQLDataSource source = new IterableSQLDataSource(connection, query);
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
	
	@Test
	public void wrongQuery() throws Exception {
		String query = "SELECT * FROM WRONG_TABLE";
		try (IterableSQLDataSource source = new IterableSQLDataSource(connection, query)) {
			IOException thrown = assertThrows(IOException.class, () -> {
				source.open();
			}, "Expected open method to throw, but it didn't");

			assertEquals("java.sql.SQLSyntaxErrorException: Table/View 'WRONG_TABLE' does not exist.", thrown.getMessage());
		}
		
	}
}
