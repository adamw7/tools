package io.github.adamw7.tools.data.uniqueness;

import java.util.Arrays;

/**
 * A row that holds fewer columns than the key reads, raised instead of the
 * {@code ArrayIndexOutOfBoundsException} the projection would otherwise fail with. The
 * key's positions come from the header, and nothing makes a data row keep to that arity:
 * a CSV line with a missing delimiter is split into a shorter array, and reading it is a
 * fault in the data rather than in the check. The bare index-out-of-bounds message named
 * neither the row nor the column, which left a caller reading somebody else's file with
 * no way to find the offending line.
 *
 * <p>The row number counts the data rows handed to the check, starting at 1: the header
 * and any line the source skips — a CSV comment — are not among them, so it is a position
 * in the data rather than in the file.</p>
 */
public class RaggedRowException extends RuntimeException {

	private final long rowNumber;
	private final int expectedColumns;
	private final int actualColumns;

	public RaggedRowException(long rowNumber, int expectedColumns, int actualColumns, String[] row) {
		super(message(rowNumber, expectedColumns, actualColumns, row));
		this.rowNumber = rowNumber;
		this.expectedColumns = expectedColumns;
		this.actualColumns = actualColumns;
	}

	private static String message(long rowNumber, int expectedColumns, int actualColumns, String[] row) {
		return "Data row " + rowNumber + " is ragged: the key reads at least " + expectedColumns
				+ " columns but the row has " + actualColumns + ": " + Arrays.toString(row);
	}

	/** The 1-based position of the offending row among the data rows read so far. */
	public long getRowNumber() {
		return rowNumber;
	}

	/** The arity the key needs, one past the highest column index it reads. */
	public int getExpectedColumns() {
		return expectedColumns;
	}

	/** The arity the offending row actually has. */
	public int getActualColumns() {
		return actualColumns;
	}
}
