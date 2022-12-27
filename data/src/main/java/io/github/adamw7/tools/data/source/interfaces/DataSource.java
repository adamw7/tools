package io.github.adamw7.tools.data.source.interfaces;

import java.io.IOException;

public interface DataSource extends AutoCloseable {

	public void open() throws IOException;
	
	public String[] nextRow() throws IOException;

	public boolean hasMoreData();
	
	public String[] getColumnNames();
	
}
