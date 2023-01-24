package io.github.adamw7.tools.data;

public class SQLCreator {

	static String table(String tableName, String[] columnNames) {
		StringBuilder sql = new StringBuilder("CREATE TABLE ");
		sql.append(tableName);
		sql.append(" (");
		for (String column : columnNames) {
			sql.append(column);
			sql.append(" VARCHAR(255),");
		}
		sql.deleteCharAt(sql.length() - 1);
		sql.append(")");
		return sql.toString();
	}

	static String table() {
		return "CREATE TABLE PEOPLE " + "(id INTEGER not NULL, " + " Name VARCHAR(255), " + " Surname VARCHAR(255),"
				+ "PRIMARY KEY ( id ))";
	}

	public static String insert() {
		return "insert into people(ID,NAME,SURNAME) values(1,'Adam','W')";
	}

	static String insert(String tableName, String[] row) {		
		StringBuilder insert = new StringBuilder("INSERT INTO ");
		insert.append(tableName);
		insert.append(" VALUES (");
		
		for (String value : row) {
			insert.append("'");
			insert.append(value);
			insert.append("',");			
		}
		insert.delete(insert.length() - 1, insert.length()); 
		insert.append(")");
		return insert.toString();
	}

	static String createSelectQueryBasedOn(String table, String[] columnNames) {
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
}
