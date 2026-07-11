package io.github.adamw7.tools.data.source.interfaces;

/**
 * An {@link IterableDataSource} whose columns are known up front, so callers can
 * ask for the schema before or while reading rows.
 *
 * <p>Forward-only sources that discover their keys as they stream — such as the
 * iterable JSON, YAML and TOON sources — deliberately do not implement this: they
 * have no fixed column set to report. Keeping {@code getColumnNames()} out of
 * {@link IterableDataSource} and here instead means a caller that needs the schema
 * (for example a uniqueness check) depends on this narrower contract and can never
 * be handed a source that would only answer with {@code null}.</p>
 */
public interface ColumnarDataSource extends IterableDataSource {

	/**
	 * The names of the columns exposed by this source, never {@code null}.
	 */
	String[] getColumnNames();
}
