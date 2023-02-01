package io.github.adamw7.tools.data.source.interfaces;

import java.io.Closeable;

public interface IterableDataSource extends AutoCloseable, Closeable {
	
	public String[] getColumnNames();
	
	public void open();
	
	public String[] nextRow();

	public boolean hasMoreData();
	
	public void reset();
	
}
