package io.github.adamw7.tools.data.uniqueness;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class Result {

	final boolean unique;
	final String[] columns;
	final String[] row;
	private final List<Result> betterOptions;
	
	public Result(boolean unique, String[] columns) {
		this(unique, columns, null, null);
	}
	
	public Result(boolean unique, String[] columns, String[] row) {
		this(unique, columns, row, new ArrayList<Result>());
	}

	public Result(boolean unique, String[] columns, String[] row, List<Result> betterOptions) {
		this.unique = unique;
		this.columns = columns;
		this.row = row;
		this.betterOptions = Collections.unmodifiableList(betterOptions);
	}
	
	public boolean isUnique() {
		return unique;
	}

	public List<Result> getBetterOptions() {
		return betterOptions;
	}
}
