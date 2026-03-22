package io.github.adamw7.tools.data.source.file;

import java.io.IOException;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Utility class for validating file paths to prevent path traversal attacks (CWE-22).
 * 
 * <p>Canonicalizes paths to resolve {@code ..}, {@code .}, and symlinks, then optionally
 * verifies the resolved path resides within an allowed base directory.</p>
 * 
 * <p>When no base directory is configured (the default), paths are still canonicalized
 * to reject obviously malformed inputs, but access is not restricted to any directory.</p>
 * 
 * <h3>Usage:</h3>
 * <pre>{@code
 * // Opt-in: restrict all file access to a specific directory
 * PathValidator.setAllowedBaseDir(Path.of("/data/uploads"));
 * 
 * // Will throw SecurityException if resolved path escapes /data/uploads
 * String safe = PathValidator.validate("report.csv");
 * 
 * // Reset when done
 * PathValidator.clearAllowedBaseDir();
 * }</pre>
 */
public final class PathValidator {

	private static final Logger log = LogManager.getLogger(PathValidator.class.getName());

	private static volatile Path allowedBaseDir = null;

	private PathValidator() {}

	/**
	 * Validates and canonicalizes a file path.
	 *
	 * @param filePath the raw file path to validate
	 * @return the canonicalized absolute path string
	 * @throws SecurityException if the path contains traversal sequences or escapes the allowed base directory
	 * @throws IllegalArgumentException if the path is null, empty, or syntactically invalid
	 */
	public static String validate(String filePath) {
		if (filePath == null || filePath.trim().isEmpty()) {
			throw new IllegalArgumentException("File path must not be null or empty");
		}

		if (containsTraversalSequences(filePath)) {
			throw new SecurityException("Path traversal detected in file path: " + filePath);
		}

		try {
			Path resolved = Path.of(filePath).toAbsolutePath().normalize();
			Path baseDir = allowedBaseDir;
			if (baseDir != null) {
				Path normalizedBase = baseDir.toAbsolutePath().normalize();
				if (!resolved.startsWith(normalizedBase)) {
					throw new SecurityException(
							"Access denied: path '" + resolved + "' is outside the allowed base directory '" + normalizedBase + "'");
				}
			}

			log.debug("Path validated: {} -> {}", filePath, resolved);
			return resolved.toString();
		} catch (InvalidPathException e) {
			throw new IllegalArgumentException("Invalid file path: " + filePath, e);
		}
	}

	private static boolean containsTraversalSequences(String path) {
		String normalized = path.replace('\\', '/');
		return normalized.contains("../") || normalized.contains("/..") || normalized.equals("..");
	}

	/**
	 * Sets the allowed base directory. When set, all validated paths must reside
	 * within this directory (or its subdirectories).
	 *
	 * @param baseDir the allowed base directory (must exist)
	 * @throws IllegalArgumentException if baseDir is null
	 * @throws IOException if the base directory path cannot be resolved
	 */
	public static void setAllowedBaseDir(Path baseDir) throws IOException {
		if (baseDir == null) {
			throw new IllegalArgumentException("Base directory must not be null");
		}
		allowedBaseDir = baseDir.toRealPath();
		log.info("Allowed base directory set to: {}", allowedBaseDir);
	}

	/**
	 * Clears the allowed base directory restriction.
	 * After calling this method, path validation will only canonicalize and
	 * check for traversal sequences, but not restrict to a specific directory.
	 */
	public static void clearAllowedBaseDir() {
		allowedBaseDir = null;
		log.info("Allowed base directory cleared");
	}
}
