package io.github.adamw7.tools.data.source.db;

/**
 * In-memory counterpart of {@link IterableParquetDataSource}: it reads a Parquet file through an
 * in-process DuckDB engine and, via {@link #readAll()}, materialises every row at once.
 *
 * <p>Like the iterable source it owns the DuckDB connection it queries, so {@link #close()} disposes
 * of that connection while {@link #reset()} keeps it for a fresh read.</p>
 */
public class InMemoryParquetDataSource extends InMemorySQLDataSource {

	public InMemoryParquetDataSource(String filePath) {
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
