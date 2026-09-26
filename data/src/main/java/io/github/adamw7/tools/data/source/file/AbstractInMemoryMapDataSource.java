package io.github.adamw7.tools.data.source.file;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import io.github.adamw7.tools.data.source.interfaces.InMemoryDataSource;

/**
 * Base class for in-memory data sources that flatten a document into a map of
 * {@code key -> value} pairs and then emit each entry as a {@code {key, value}} row.
 *
 * <p>Subclasses only need to parse the document into {@link #fieldsMap}; the
 * row-iteration lifecycle (open/next/hasMoreData/reset/iterator) is shared here.
 * The map keeps insertion order, so rows come out in the order the document
 * declares them &mdash; the order the matching iterable source streams them in.</p>
 *
 * <p>Every row has the same two columns, {@value #KEY_COLUMN} and
 * {@value #VALUE_COLUMN}, and {@link #getColumnNames()} names exactly those. The
 * flattened keys are data here, not a schema: a column check such as the uniqueness
 * check addresses a row by the position of a name in {@link #getColumnNames()}, so
 * naming every key there would point it past the end of a two-column row.</p>
 */
public abstract class AbstractInMemoryMapDataSource extends AbstractFileSource implements InMemoryDataSource {

	/** The column holding a flattened key, such as {@code people[0].address.city}. */
	public static final String KEY_COLUMN = "key";
	/** The column holding the value found at that key. */
	public static final String VALUE_COLUMN = "value";

	protected final Map<String, String> fieldsMap = new LinkedHashMap<>();
	private Iterator<String> mapIterator;

	protected AbstractInMemoryMapDataSource(InputStream inputStream) {
		super(inputStream);
		scanner = createScanner(inputStream);
		parse();
	}

	protected AbstractInMemoryMapDataSource(String filePath) {
		super(filePath);
		parse();
	}

	protected AbstractInMemoryMapDataSource(String filePath, AllowedPaths allowedPaths) {
		super(filePath, allowedPaths);
		parse();
	}

	/** Parses the open {@link #scanner} into {@link #fieldsMap}. */
	protected abstract void parse();

	@Override
	public void open() {
		if (opened) {
			throw new IllegalStateException("DataSource is already open");
		}
		mapIterator = fieldsMap.keySet().iterator();
		opened = true;
	}

	/**
	 * The next {@code {key, value}} pair, or {@code null} once the map is exhausted, as
	 * {@link io.github.adamw7.tools.data.source.interfaces.IterableDataSource#nextRow()}
	 * defines. Here the two answers line up exactly with {@link #hasMoreData()}: every
	 * entry yields a row, so {@code null} only ever means the end.
	 */
	@Override
	public String[] nextRow() {
		checkIfOpen();
		if (mapIterator.hasNext()) {
			String key = mapIterator.next();
			String value = fieldsMap.get(key);
			return new String[] { key, value };
		}
		return null;
	}

	@Override
	public boolean hasMoreData() {
		checkIfOpen();
		return mapIterator.hasNext();
	}

	@Override
	public void reset() {
		mapIterator = fieldsMap.keySet().iterator();
		opened = true;
	}

	public Iterator<String[]> iterator() {
		return new Iterator<>() {
			@Override
			public boolean hasNext() {
				return hasMoreData();
			}

			@Override
			public String[] next() {
				return nextRow();
			}
		};
	}

	/** @return {@value #KEY_COLUMN} and {@value #VALUE_COLUMN}, the two columns every row has */
	@Override
	public String[] getColumnNames() {
		return new String[] { KEY_COLUMN, VALUE_COLUMN };
	}

	/**
	 * Returns every {@code {key, value}} pair, built straight from {@link #fieldsMap}.
	 * The document is already fully parsed into the map, so this neither needs nor
	 * calls {@link #open()} &mdash; which lets callers that have already opened the
	 * source (such as the uniqueness checks) read it without tripping the
	 * open-once guard, and makes {@code readAll} usable regardless of open state.
	 */
	@Override
	public List<String[]> readAll() {
		List<String[]> data = new ArrayList<>(fieldsMap.size());
		for (Map.Entry<String, String> entry : fieldsMap.entrySet()) {
			data.add(new String[] { entry.getKey(), entry.getValue() });
		}
		return data;
	}
}
