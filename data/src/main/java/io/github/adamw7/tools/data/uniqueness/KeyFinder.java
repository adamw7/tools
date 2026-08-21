package io.github.adamw7.tools.data.uniqueness;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

public class KeyFinder {

	private final int[] indices;
	private final int expectedColumns;
	private final Set<Key> set = new HashSet<>();
	private long rowNumber = 0;

	public KeyFinder(int[] indices) {
		this.indices = indices;
		this.expectedColumns = Arrays.stream(indices).max().orElse(-1) + 1;
	}

	public boolean found(String[] row) {
		if (row == null) {
			return false;
		}
		++rowNumber;
		return !set.add(key(row));
	}

	protected Key key(String[] row) {
		checkArity(row);
		return new Key(Arrays.stream(indices).mapToObj(index -> row[index]).toArray(String[]::new));
	}

	/**
	 * Refuses a row too short for the key before projecting it. The indices come from the
	 * header, so a row that arrives with fewer columns than the file declares — a CSV line
	 * with a delimiter missing — is data the caller has to fix, and it is reported as such
	 * instead of reaching them as an {@code ArrayIndexOutOfBoundsException} naming nothing.
	 */
	private void checkArity(String[] row) {
		if (row.length < expectedColumns) {
			throw new RaggedRowException(rowNumber, expectedColumns, row.length, row);
		}
	}
}
