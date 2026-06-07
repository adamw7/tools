package io.github.adamw7.tools.data.source.interfaces;

import java.io.Closeable;
import java.util.ArrayList;
import java.util.List;

public interface IterableDataSource extends Closeable {

	String[] getColumnNames();

	void open();

	String[] nextRow();

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
