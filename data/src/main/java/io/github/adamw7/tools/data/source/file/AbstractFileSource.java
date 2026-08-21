package io.github.adamw7.tools.data.source.file;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;

import io.github.adamw7.tools.data.compression.ZipUtils;
import io.github.adamw7.tools.data.source.interfaces.IterableDataSource;

public abstract class AbstractFileSource implements IterableDataSource {
	protected Scanner scanner;
	protected String fileName;
	protected InputStream inputStream;
	protected boolean opened = false;
	
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
		this.fileName = allowedPaths.validate(fileName);
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

	protected void checkIfOpen() {
		if (!opened) {
			throw new IllegalStateException("DataSource is not open");
		}
	}
	
	/**
	 * The last element of the path this source reads, which a caller naming its columns
	 * after the file it came from asks for. A path that names no file — a filesystem root
	 * such as {@code /} or {@code C:\} — is refused in the same terms as a raw stream,
	 * rather than dereferencing the {@code null} {@link Path#getFileName}
	 * answers for it.
	 */
	public String getFileName() {
		if (fileName == null) {
			throw new IllegalStateException("Source is backed by a raw input stream, not a file");
		}
		Path name = Paths.get(fileName).getFileName();
		if (name == null) {
			throw new IllegalStateException("Source path names no file: " + fileName);
		}
		return name.toString();
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
