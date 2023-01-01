package io.github.adamw7.tools.data.uniqueness;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

public class Result {

	final boolean unique;
	final String[] columns;
	final String[] row;
	private final Set<Result> betterOptions;
	
	@Override
	public String toString() {
		return "Result [unique=" + unique + ", columns=" + Arrays.toString(columns) + ", row=" + Arrays.toString(row)
				+ ", betterOptions=" + betterOptions + "]";
	}

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
	
	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + Arrays.hashCode(columns);
		result = prime * result + Arrays.hashCode(row);
		result = prime * result + Objects.hash(betterOptions, unique);
		return result;
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (getClass() != obj.getClass())
			return false;
		Result other = (Result) obj;
		return Objects.equals(betterOptions, other.betterOptions) && Arrays.equals(columns, other.columns)
				&& Arrays.equals(row, other.row) && unique == other.unique;
	}
}
