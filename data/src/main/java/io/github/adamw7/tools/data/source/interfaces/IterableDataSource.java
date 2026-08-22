package io.github.adamw7.tools.data.source.interfaces;

import java.io.Closeable;
import java.util.ArrayList;
import java.util.List;

public interface IterableDataSource extends Closeable {

	void open();

	/**
	 * The next row of this source, or {@code null} when there is none to hand back.
	 *
	 * <p>{@code null} is the contract here, not an oversight, and a zero-length array
	 * would not replace it: the row a source produces may itself be empty &mdash; a blank
	 * line in a CSV splits to one empty column, and a delimiter-only line to several
	 * &mdash; so an empty array is a row, and only {@code null} can mean "no row". It
	 * carries two meanings, told apart by {@link #hasMoreData()} rather than by the value:
	 * the source is exhausted, or this particular input line produced nothing to emit and
	 * a further call may still return a row (a CSV comment is the case that does this).
	 * Callers that walk a source therefore pair this with {@link #hasMoreData()} and drop
	 * the {@code null}s, as {@link #nextRows(int)} does.</p>
	 *
	 * @return the next row, or {@code null} if this call produced none
	 */
	String[] nextRow();

	/**
	 * Whether a further {@link #nextRow()} can still produce a row. A source that answers
	 * {@code true} may still return {@code null} from the next call &mdash; see there.
	 */
	boolean hasMoreData();

	void reset();

	/**
	 * Loads up to {@code batchSize} rows in a single operation, letting callers control how
	 * much data is pulled from the source at once instead of reading row by row.
	 *
	 * <p>Fewer rows are returned when the source is exhausted, so an empty list signals that
	 * there is no more data. Rows that the underlying source skips (for example comment lines)
	 * do not count towards the batch.</p>
	 *
	 * @param batchSize maximum number of rows to load, must be positive
	 * @return the loaded rows, never {@code null}
	 */
	default List<String[]> nextRows(int batchSize) {
		if (batchSize <= 0) {
			throw new IllegalArgumentException("batchSize must be positive but was " + batchSize);
		}
		List<String[]> batch = new ArrayList<>(batchSize);
		while (batch.size() < batchSize && hasMoreData()) {
			String[] row = nextRow();
			if (row != null) {
				batch.add(row);
			}
		}
		return batch;
	}

}
