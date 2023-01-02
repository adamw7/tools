package io.github.adamw7.tools.data.source.interfaces;

import java.io.IOException;
import java.util.List;

public interface InMemoryDataSource extends IterableDataSource {

	public List<String[]> readAll() throws IOException;
}
