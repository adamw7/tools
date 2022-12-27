package io.github.adamw7.tools.data.uniqueness;

public class Result {

	final boolean unique;
	final String[] columns;
	final String[] row;

	public Result(boolean unique, String[] columns, String[] row) {
		this.unique = unique;
		this.columns = columns;
		this.row = row;
	}
	
	public boolean isUnique() {
		return unique;
	}

}
