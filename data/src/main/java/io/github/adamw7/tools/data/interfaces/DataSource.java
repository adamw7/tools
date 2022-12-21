package io.github.adamw7.tools.data.interfaces;

import java.io.IOException;

public interface DataSource extends AutoCloseable {

	public void open();
	
	public String[] nextRow() throws IOException;

	public boolean hasMoreData();
	
}
