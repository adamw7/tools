package io.github.adamw7.tools.data.source.interfaces;

import java.io.Closeable;

public interface IterableDataSource extends AutoCloseable, Closeable {
	
	String[] getColumnNames();
	
	void open();
	
	String[] nextRow();

	boolean hasMoreData();
	
	void reset();
	
}
