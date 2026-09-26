package io.github.adamw7.tools.data.source.file;

import java.io.InputStream;
import java.nio.file.Path;
import java.nio.file.Paths;

import io.github.adamw7.tools.data.source.interfaces.IterableDataSource;

/**
 * What every file-backed source shares, whether it reads its file eagerly
 * ({@link AbstractFileSource}) or opens it lazily ({@link AbstractIterableFileSource}):
 * the path it reads, validated against the boundary it is confined to, or else the
 * raw stream it was handed; and whether it is open. The two lifecycles stay in their
 * own subclasses, since one holds a {@link java.util.Scanner} from construction and
 * the other opens a stream on every {@code open()}.
 */
public abstract class AbstractFileBackedSource implements IterableDataSource {

	/** The validated path this source reads, or {@code null} when it reads a raw stream. */
	protected final String fileName;

	/** The raw stream this source reads, or {@code null} when it reads {@link #fileName}. */
	protected final InputStream inputStream;

	protected boolean opened;

	/**
	 * Reads {@code fileName} after checking it against {@code allowedPaths}, the boundary
	 * this source is confined to.
	 */
	protected AbstractFileBackedSource(String fileName, AllowedPaths allowedPaths) {
		this.fileName = allowedPaths.validate(fileName);
		this.inputStream = null;
	}

	protected AbstractFileBackedSource(InputStream inputStream) {
		this.fileName = null;
		this.inputStream = inputStream;
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
}
