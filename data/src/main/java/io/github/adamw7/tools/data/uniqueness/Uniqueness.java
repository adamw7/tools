package io.github.adamw7.tools.data.uniqueness;

import io.github.adamw7.tools.data.source.interfaces.IterableDataSource;

public interface Uniqueness {
	Result exec(String... keyCandidates);
	
	<T extends IterableDataSource> void setDataSource(T source);
}