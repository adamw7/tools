package io.github.adamw7.tools.data.uniqueness;

import io.github.adamw7.tools.data.source.interfaces.InMemoryDataSource;

public class InMemoryUniquenessCheck extends Uniqueness {
	
	private final InMemoryDataSource dataSource;

	public InMemoryUniquenessCheck(InMemoryDataSource dataSource) {
		this.dataSource = dataSource;
	}
	
	@Override
	public Result exec(String... keyCandidates) throws Exception {
		// TODO Auto-generated method stub
		return null;
	}

}
