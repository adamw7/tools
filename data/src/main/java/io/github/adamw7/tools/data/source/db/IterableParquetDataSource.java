package io.github.adamw7.tools.data.source.db;

import io.github.adamw7.tools.data.source.interfaces.ColumnarDataSource;

/**
 * Reads a Parquet file as a {@link ColumnarDataSource} by streaming it through an in-process
 * DuckDB engine, so a Parquet file's columns and rows are exposed exactly like any other
 * JDBC-backed source.
 *
 * <p>Unlike {@link IterableSQLDataSource}, which borrows a caller-owned {@link java.sql.Connection},
 * this source creates and owns the DuckDB connection it queries. {@link #close()} therefore disposes
 * of that connection, while {@link #reset()} keeps it so the file can be re-read.</p>
 */
public class IterableParquetDataSource extends IterableSQLDataSource {

	public IterableParquetDataSource(String filePath) {
		super(DuckDbParquet.connect(), DuckDbParquet.readQuery(filePath));
	}

	@Override
	public void reset() {
		releaseQueryResources();
		hasMoreData = true;
		open();
	}

	@Override
	public void close() {
		releaseQueryResources();
		DuckDbParquet.close(connection);
	}

	private void releaseQueryResources() {
		super.close();
	}
}
