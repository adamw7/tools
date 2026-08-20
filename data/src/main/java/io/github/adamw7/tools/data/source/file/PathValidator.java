package io.github.adamw7.tools.data.source.file;

import java.io.IOException;
import java.nio.file.Path;

/**
 * Process-wide path validation, superseded by {@link AllowedPaths}.
 *
 * <p>Every entry point here reads or writes one boundary shared by the whole JVM: two
 * sources cannot be confined to two directories, and any code can lift the restriction
 * for everything else in the process by calling {@link #clearAllowedBaseDir()}. Hand an
 * {@link AllowedPaths} to the source it confines instead:</p>
 *
 * <pre>{@code
 * new InMemoryCSVDataSource(fileName, 1, AllowedPaths.under(Path.of("/data/uploads")));
 * }</pre>
 *
 * <p>The methods below remain for one release so callers can migrate, and are the only
 * thing this class exists for; it goes away with them. The class itself carries no
 * {@code @Deprecated} annotation, so that the data sources reading {@link #shared()}
 * internally do not report a deprecation they cannot avoid while these methods stand.</p>
 */
public final class PathValidator {

	private static volatile AllowedPaths shared = AllowedPaths.anywhere();

	private PathValidator() {}

	/**
	 * The boundary the {@code String}-taking data-source constructors validate against
	 * when the caller supplies none. Not deprecated, because it is how those constructors
	 * keep honouring a base directory set through this class; once the methods below are
	 * gone it becomes {@link AllowedPaths#anywhere()}.
	 */
	static AllowedPaths shared() {
		return shared;
	}

	/**
	 * Validates and canonicalizes a file path against the process-wide base directory.
	 *
	 * @param filePath the raw file path to validate
	 * @return the canonicalized absolute path string
	 * @throws SecurityException if the path contains traversal sequences or escapes the allowed base directory
	 * @throws IllegalArgumentException if the path is null, empty, or syntactically invalid
	 * @deprecated call {@link AllowedPaths#validate(String)} on the instance confining the caller
	 */
	@Deprecated(since = "2.6.0", forRemoval = true)
	public static String validate(String filePath) {
		return shared.validate(filePath);
	}

	/**
	 * Sets the allowed base directory for the whole JVM. When set, all validated paths
	 * must reside within this directory (or its subdirectories).
	 *
	 * @param baseDir the allowed base directory (must exist)
	 * @throws IllegalArgumentException if baseDir is null
	 * @throws IOException if the base directory path cannot be resolved
	 * @deprecated build an {@link AllowedPaths#under(Path)} and pass it to the source it confines
	 */
	@Deprecated(since = "2.6.0", forRemoval = true)
	public static void setAllowedBaseDir(Path baseDir) throws IOException {
		shared = AllowedPaths.under(baseDir);
	}

	/**
	 * Clears the process-wide base directory restriction. After calling this method,
	 * path validation will only canonicalize and check for traversal sequences, but not
	 * restrict to a specific directory.
	 *
	 * @deprecated pass {@link AllowedPaths#anywhere()} to the source that should be unrestricted
	 */
	@Deprecated(since = "2.6.0", forRemoval = true)
	public static void clearAllowedBaseDir() {
		shared = AllowedPaths.anywhere();
	}
}
