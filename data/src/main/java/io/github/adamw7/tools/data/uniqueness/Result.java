package io.github.adamw7.tools.data.uniqueness;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

public class Result {

	final boolean unique;
	final String[] columns;
	final String[] row;
	private final Set<Result> betterOptions;
	
	public Result(boolean unique, String[] columns) {
		this(unique, columns, null, new HashSet<Result>());
	}
	
	public Result(boolean unique, String[] columns, String[] row) {
		this(unique, columns, row, new HashSet<Result>());
	}

	public Result(boolean unique, String[] columns, String[] row, Set<Result> betterOptions) {
		this.unique = unique;
		this.columns = columns;
		this.row = row;
		this.betterOptions = Collections.unmodifiableSet(betterOptions);
	}
	
	public boolean isUnique() {
		return unique;
	}

	public Set<Result> getBetterOptions() {
		return betterOptions;
	}
}
