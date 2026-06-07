package io.github.adamw7.tools.data.uniqueness;

import io.github.adamw7.tools.data.source.interfaces.IterableDataSource;

public interface Uniqueness<T extends IterableDataSource> {
	Result exec(String... keyCandidates);

	Result exec();

	void setDataSource(T source);
}
