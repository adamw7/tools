package io.github.adamw7.tools.data.source.db;

import io.github.adamw7.tools.data.source.file.AllowedPaths;
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

	/**
	 * Reads {@code filePath} unconfined: the path is canonicalised and refused if it climbs
	 * out with {@code ..}, but may name any file. Prefer
	 * {@link #IterableParquetDataSource(String, AllowedPaths)}.
	 */
	public IterableParquetDataSource(String filePath) {
		this(filePath, AllowedPaths.anywhere());
	}

	/**
	 * The path is validated before the DuckDB connection is opened, so a refused path leaves
	 * no connection behind.
	 *
	 * @param allowedPaths the directory this source may read from; a path outside it is refused
	 *                     with a {@link SecurityException}
	 */
	public IterableParquetDataSource(String filePath, AllowedPaths allowedPaths) {
		String query = DuckDbParquet.readQuery(filePath, allowedPaths);
		super(DuckDbParquet.connect(), query);
	}

	@Override
	public void close() {
		super.close();
		DuckDbParquet.close(connection);
	}
}
