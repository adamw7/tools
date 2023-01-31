package io.github.adamw7.tools.data.uniqueness;

import java.io.IOException;

import io.github.adamw7.tools.data.source.interfaces.IterableDataSource;

public interface Uniqueness {
	public Result exec(String... keyCandidates) throws IOException;
	
	public <T extends IterableDataSource> void setDataSource(T source);
}