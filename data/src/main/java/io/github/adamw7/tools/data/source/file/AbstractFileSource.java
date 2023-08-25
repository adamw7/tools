package io.github.adamw7.tools.data.source.file;

import java.io.Closeable;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;

import io.github.adamw7.tools.data.compression.ZipUtils;
import io.github.adamw7.tools.data.source.interfaces.IterableDataSource;

public abstract class AbstractFileSource implements AutoCloseable, Closeable, IterableDataSource {
	protected Scanner scanner;
	protected String fileName;
	protected InputStream inputStream;
	protected boolean opened = false;
	
	@Override
	public void close() throws IOException {
		if (opened) {
			scanner.close();
			opened = false;
		}
	}
	
	protected AbstractFileSource(String fileName) {
		this.fileName = fileName;
		try {
			scanner = createScanner();
		} catch (FileNotFoundException e) {
			throw new UncheckedIOException(e);
		}
	}

	protected AbstractFileSource(InputStream inputStream) {
		this.inputStream = inputStream;
	}

	protected Scanner createScanner() throws FileNotFoundException {
		if (fileName != null) {
			return createScanner(fileName);
		} else if (inputStream != null) {
			return createScanner(inputStream);
		} else {
			throw new IllegalStateException("Both input stream and file are nulls");
		}
	}
	
	protected Scanner createScanner(InputStream inputStream) {
		return new Scanner(ZipUtils.unzipIfNeeded(inputStream, fileName), StandardCharsets.UTF_8);
	}

	protected Scanner createScanner(String fileName) throws FileNotFoundException {
		return createScanner(new FileInputStream(fileName));
	}
	
	protected void checkIfOpen() {
		if (!opened) {
			throw new IllegalStateException("DataSource is not open");
		}
	}
	
	public String getFileName() {
		return Paths.get(fileName).getFileName().toString();
	}
	
	protected List<String[]> readAll() {
		open();
		List<String[]> data = new ArrayList<>();
		while (hasMoreData()) {
			String[] row = nextRow();
			if (row != null) {
				data.add(row);	
			}
		}
		return data;
	}
}
