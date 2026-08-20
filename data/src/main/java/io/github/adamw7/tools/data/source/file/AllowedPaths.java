package io.github.adamw7.tools.data.source.file;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.Arrays;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * The directory a file source may read from, and the check that holds it there
 * (path traversal, CWE-22).
 *
 * <p>An instance is immutable and is handed to the source it confines, so a host
 * embedding this library can point two sources at two different roots, and no
 * unrelated code can widen or narrow a boundary somebody else set. Paths are
 * canonicalized either way, so a malformed path is rejected even by an
 * {@link #anywhere() unconfined} instance.</p>
 *
 * <h3>Usage:</h3>
 * <pre>{@code
 * // Restrict this source to one directory
 * AllowedPaths uploads = AllowedPaths.under(Path.of("/data/uploads"));
 *
 * // Throws SecurityException if the resolved path escapes /data/uploads
 * InMemoryDataSource source = new InMemoryCSVDataSource("report.csv", 1, uploads);
 *
 * // Or leave a source unrestricted
 * new InMemoryCSVDataSource("report.csv", 1, AllowedPaths.anywhere());
 * }</pre>
 */
public final class AllowedPaths {

	private static final Logger log = LogManager.getLogger(AllowedPaths.class.getName());

	private static final AllowedPaths ANYWHERE = new AllowedPaths(null);

	/** The root every validated path must sit under, or {@code null} when unconfined. */
	private final Path baseDir;

	private AllowedPaths(Path baseDir) {
		this.baseDir = baseDir;
	}

	/**
	 * Paths are canonicalized and checked for traversal sequences, but not restricted
	 * to any directory. This is what a source built without a boundary uses.
	 *
	 * @return the shared unconfined instance
	 */
	public static AllowedPaths anywhere() {
		return ANYWHERE;
	}

	/**
	 * Confines validated paths to {@code baseDir} and its subdirectories.
	 *
	 * @param baseDir the allowed base directory (must exist)
	 * @return a validator confined to that directory
	 * @throws IllegalArgumentException if baseDir is null
	 * @throws IOException if the base directory path cannot be resolved
	 */
	public static AllowedPaths under(Path baseDir) throws IOException {
		if (baseDir == null) {
			throw new IllegalArgumentException("Base directory must not be null");
		}
		Path resolvedBase = baseDir.toRealPath();
		log.info("Confining file access to base directory: {}", resolvedBase);
		return new AllowedPaths(resolvedBase);
	}

	/**
	 * Validates and canonicalizes a file path.
	 *
	 * @param filePath the raw file path to validate
	 * @return the canonicalized absolute path string
	 * @throws SecurityException if the path contains traversal sequences or escapes the allowed base directory
	 * @throws IllegalArgumentException if the path is null, empty, or syntactically invalid
	 */
	public String validate(String filePath) {
		if (filePath == null || filePath.trim().isEmpty()) {
			throw new IllegalArgumentException("File path must not be null or empty");
		}

		if (containsTraversalSequences(filePath)) {
			throw new SecurityException("Path traversal detected in file path: " + filePath);
		}

		try {
			Path resolved = Path.of(filePath).toAbsolutePath().normalize();
			if (baseDir != null) {
				checkInsideBaseDir(resolved);
			}

			log.debug("Path validated: {} -> {}", filePath, resolved);
			return resolved.toString();
		} catch (InvalidPathException e) {
			throw new IllegalArgumentException("Invalid file path: " + filePath, e);
		}
	}

	private void checkInsideBaseDir(Path resolved) {
		Path realResolved = toRealPathAllowingMissing(resolved);
		if (!realResolved.startsWith(baseDir)) {
			throw new SecurityException(
					"Access denied: path '" + realResolved + "' is outside the allowed base directory '" + baseDir + "'");
		}
	}

	/**
	 * Resolves {@code path} to its real, symlink-followed location so the containment
	 * check cannot be bypassed by a symlink that lives inside the base directory but
	 * points outside it. A purely textual {@code normalize()} would collapse {@code ..}
	 * without ever following the link, letting {@code <base>/link/secret} slip past when
	 * {@code link} targets {@code /etc}. The nearest existing ancestor is canonicalised
	 * with {@link Path#toRealPath()} and the not-yet-created remainder re-appended, so the
	 * check works whether or not the leaf file exists yet.
	 */
	private static Path toRealPathAllowingMissing(Path path) {
		Path existing = path;
		while (existing != null && !Files.exists(existing)) {
			existing = existing.getParent();
		}
		if (existing == null) {
			return path;
		}
		try {
			Path realExisting = existing.toRealPath();
			return realExisting.resolve(existing.relativize(path)).normalize();
		} catch (IOException e) {
			throw new UncheckedIOException("Could not resolve real path for: " + path, e);
		}
	}

	/**
	 * Looks for a {@code ..} that is a path element of its own, rather than for the
	 * substring: a file legitimately named {@code ..name} contains {@code /..} without
	 * ever climbing out of its directory, and used to be refused for it.
	 */
	private static boolean containsTraversalSequences(String path) {
		String normalized = path.replace('\\', '/');
		return Arrays.stream(normalized.split("/", -1)).anyMatch(".."::equals);
	}
}
