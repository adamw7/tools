package io.github.adamw7.tools.data.source.file;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;

import io.github.adamw7.tools.data.compression.ZipUtils;

/**
 * Base for the file sources that hold a {@link Scanner} over their input from the moment
 * they are built: the CSV sources and the map-backed in-memory JSON, YAML and TOON ones.
 */
public abstract class AbstractFileSource extends AbstractFileBackedSource {
	protected Scanner scanner;
	
	@Override
	public void close() throws IOException {
		if (scanner != null) {
			// Closed regardless of the opened flag: the constructor already
			// opened the underlying file, so a source that is closed before
			// open() must still release its handle.
			scanner.close();
		}
		opened = false;
	}
	
	/**
	 * Reads {@code fileName} under the process-wide base directory, if one was set through
	 * the deprecated {@code PathValidator} entry points, and unrestricted otherwise. Prefer
	 * {@link #AbstractFileSource(String, AllowedPaths)}, which confines this source alone.
	 */
	protected AbstractFileSource(String fileName) {
		this(fileName, PathValidator.shared());
	}

	/**
	 * Reads {@code fileName} after checking it against {@code allowedPaths}, the boundary
	 * this source is confined to.
	 */
	protected AbstractFileSource(String fileName, AllowedPaths allowedPaths) {
		super(fileName, allowedPaths);
		try {
			scanner = createScanner();
		} catch (FileNotFoundException e) {
			throw new UncheckedIOException(e);
		}
	}

	protected AbstractFileSource(InputStream inputStream) {
		super(inputStream);
		scanner = createScanner(inputStream);
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
		FileInputStream stream = new FileInputStream(fileName);
		try {
			return createScanner(stream);
		} catch (RuntimeException e) {
			closeAfterFailure(stream, e);
			throw e;
		}
	}

	/**
	 * Releases {@code stream} on the failure path of {@link #createScanner(String)}, where no
	 * {@link Scanner} was built to take ownership of it and nothing else holds a reference to
	 * it. A GZip member whose magic number promises more than the file delivers is the way in:
	 * {@link ZipUtils#unzipIfNeeded(InputStream, String)} reads the header to wrap the stream,
	 * so it fails with the descriptor already open. A failure to close is attached to
	 * {@code failure} rather than replacing it, since the reason the wrapping failed is the one
	 * worth reporting.
	 */
	private static void closeAfterFailure(InputStream stream, RuntimeException failure) {
		try {
			stream.close();
		} catch (IOException e) {
			failure.addSuppressed(e);
		}
	}
	
	/**
	 * Answers whether the scan has another line, failing rather than reporting a clean end
	 * of data when the read behind it broke. {@link Scanner} never throws
	 * {@link IOException}: it swallows a read failure, stops producing tokens, and keeps
	 * the cause in {@link Scanner#ioException()}. Left unread, that turns a truncated
	 * transfer, a corrupt GZip member or a disk error into an ordinary end of file, and a
	 * caller such as the uniqueness check then answers "this column is unique" having seen
	 * only the rows that arrived.
	 */
	protected boolean hasNextLine() {
		if (scanner.hasNextLine()) {
			return true;
		}
		IOException failure = scanner.ioException();
		if (failure != null) {
			throw new UncheckedIOException("Reading " + describeSource() + " failed before the end of the data",
					failure);
		}
		return false;
	}

	private String describeSource() {
		return fileName != null ? fileName : "the input stream";
	}

	/**
	 * Opens this source and drains it into a list, skipping the rows {@link #nextRow()}
	 * declines to produce. It is the shared machinery behind {@code readAll()} on the
	 * in-memory sources built on this class, and deliberately not called that: only a
	 * {@link io.github.adamw7.tools.data.source.interfaces.InMemoryDataSource} publishes
	 * a read-everything operation, and naming this one {@code readAll} would have every
	 * forward-only source built here widen it to public just by implementing that
	 * interface &mdash; putting the operation on the public surface of sources whose
	 * whole point is that they do not offer it.
	 */
	protected List<String[]> readAllRows() {
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
