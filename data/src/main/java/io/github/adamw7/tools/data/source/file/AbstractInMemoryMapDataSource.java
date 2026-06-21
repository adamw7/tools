package io.github.adamw7.tools.data.source.file;

import java.io.InputStream;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import io.github.adamw7.tools.data.source.interfaces.InMemoryDataSource;

/**
 * Base class for in-memory data sources that flatten a document into a map of
 * {@code key -> value} pairs and then emit each entry as a {@code {key, value}} row.
 *
 * <p>Subclasses only need to parse the document into {@link #fieldsMap}; the
 * row-iteration lifecycle (open/next/hasMoreData/reset/iterator) is shared here.</p>
 */
public abstract class AbstractInMemoryMapDataSource extends AbstractFileSource implements InMemoryDataSource {

	protected final Map<String, String> fieldsMap = new HashMap<>();
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
		checkIfOpen();
		mapIterator = fieldsMap.keySet().iterator();
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

	@Override
	public String[] getColumnNames() {
		return fieldsMap.keySet().toArray(new String[] {});
	}

	@Override
	public List<String[]> readAll() {
		return super.readAll();
	}
}
