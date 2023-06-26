package io.github.adamw7.tools.data.source.interfaces;

import java.util.List;

public interface InMemoryDataSource extends IterableDataSource {

	List<String[]> readAll();
}
