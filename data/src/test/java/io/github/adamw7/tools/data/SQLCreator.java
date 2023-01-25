package io.github.adamw7.tools.data;

public class SQLCreator {

	public static String table(String tableName, String[] columnNames) {
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

	public static String insert(String tableName, String[] row) {		
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

	public static String createSelectQueryBasedOn(String table, String[] columnNames) {
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
