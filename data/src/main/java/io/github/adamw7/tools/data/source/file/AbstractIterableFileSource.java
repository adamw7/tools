package io.github.adamw7.tools.data.source.file;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;

import io.github.adamw7.tools.data.compression.ZipUtils;
import io.github.adamw7.tools.data.source.interfaces.IterableDataSource;

/**
 * Base class for file data sources that emit rows incrementally without loading the whole
 * document into memory. A single-row lookahead lets {@link #hasMoreData()} and
 * {@link #nextRow()} stay accurate while only the current row (plus any per-format parser
 * state) is held in memory.
 */
public abstract class AbstractIterableFileSource implements IterableDataSource {

	protected final String fileName;
	private final InputStream providedStream;
	protected boolean opened = false;
	private String[] lookahead;

	protected AbstractIterableFileSource(String fileName) {
		this.fileName = PathValidator.validate(fileName);
		this.providedStream = null;
	}

	protected AbstractIterableFileSource(InputStream inputStream) {
		this.fileName = null;
		this.providedStream = inputStream;
	}

	@Override
	public void open() {
		if (opened) {
			throw new IllegalStateException("DataSource is already open");
		}
		try {
			initialise(openStream());
			opened = true;
			lookahead = readNextRow();
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}

	private InputStream openStream() throws IOException {
		InputStream raw = providedStream != null ? providedStream : new FileInputStream(fileName);
		return ZipUtils.unzipIfNeeded(raw, fileName);
	}

	@Override
	public String[] nextRow() {
		checkIfOpen();
		String[] current = lookahead;
		lookahead = advance();
		return current;
	}

	private String[] advance() {
		try {
			return readNextRow();
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}

	@Override
	public boolean hasMoreData() {
		checkIfOpen();
		return lookahead != null;
	}

	/**
	 * Iterable sources do not know the full set of columns up front, so this returns
	 * {@code null}.
	 */
	@Override
	public String[] getColumnNames() {
		return null;
	}

	@Override
	public void reset() {
		if (fileName == null) {
			throw new IllegalStateException("Cannot reset a source backed by a raw input stream");
		}
		closeQuietly();
		open();
	}

	@Override
	public void close() throws IOException {
		if (opened) {
			releaseResources();
			opened = false;
			lookahead = null;
		}
	}

	private void closeQuietly() {
		try {
			close();
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}

	protected void checkIfOpen() {
		if (!opened) {
			throw new IllegalStateException("DataSource is not open");
		}
	}

	/** Prepares the per-format parser/reader over the freshly opened stream. */
	protected abstract void initialise(InputStream stream) throws IOException;

	/** Returns the next flattened row, or {@code null} when the document is exhausted. */
	protected abstract String[] readNextRow() throws IOException;

	/** Closes the per-format parser/reader (which owns and closes the stream). */
	protected abstract void releaseResources() throws IOException;
}
