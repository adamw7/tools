package io.github.adamw7.tools.data.source.interfaces;

import java.io.IOException;

public interface IterableDataSource extends AutoCloseable {
	
	public String[] getColumnNames();
	
	public void open() throws IOException;
	
	public String[] nextRow() throws IOException;

	public boolean hasMoreData();
	
	public void reset() throws IOException;
	
}
