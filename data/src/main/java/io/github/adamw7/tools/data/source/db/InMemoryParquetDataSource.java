package io.github.adamw7.tools.data.source.db;

import io.github.adamw7.tools.data.source.file.AllowedPaths;

/**
 * In-memory counterpart of {@link IterableParquetDataSource}: it reads a Parquet file through an
 * in-process DuckDB engine and, via {@link #readAll()}, materialises every row at once.
 *
 * <p>Like the iterable source it owns the DuckDB connection it queries, so {@link #close()} disposes
 * of that connection while {@link #reset()} keeps it for a fresh read.</p>
 */
public class InMemoryParquetDataSource extends InMemorySQLDataSource {

	/**
	 * Reads {@code filePath} unconfined: the path is canonicalised and refused if it climbs
	 * out with {@code ..}, but may name any file. Prefer
	 * {@link #InMemoryParquetDataSource(String, AllowedPaths)}.
	 */
	public InMemoryParquetDataSource(String filePath) {
		this(filePath, AllowedPaths.anywhere());
	}

	/**
	 * The path is validated before the DuckDB connection is opened, so a refused path leaves
	 * no connection behind.
	 *
	 * @param allowedPaths the directory this source may read from; a path outside it is refused
	 *                     with a {@link SecurityException}
	 */
	public InMemoryParquetDataSource(String filePath, AllowedPaths allowedPaths) {
		String query = DuckDbParquet.readQuery(filePath, allowedPaths);
		super(DuckDbParquet.connect(), query);
	}

	@Override
	public void close() {
		super.close();
		DuckDbParquet.close(connection);
	}
}
