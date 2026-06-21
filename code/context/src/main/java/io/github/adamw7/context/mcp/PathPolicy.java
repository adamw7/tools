package io.github.adamw7.context.mcp;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;

/**
 * Confines the file-system access of the MCP tools to a set of allowed root
 * directories. Every caller-supplied path is resolved to its real location
 * (following symlinks and collapsing {@code ..}) and must lie within one of the
 * configured roots, so a malicious or confused client cannot steer the project
 * scanners at arbitrary files such as {@code /etc} or a user's home directory.
 * Roots are configured through {@code context.allowed-roots} as a
 * {@link File#pathSeparator}-separated list; when none are configured the
 * server's working directory becomes the single allowed root.
 */
final class PathPolicy {

	private final List<Path> allowedRoots;

	PathPolicy(String configuredRoots) {
		this.allowedRoots = resolveRoots(configuredRoots);
	}

	List<Path> allowedRoots() {
		return allowedRoots;
	}

	Path resolve(String requestedPath) {
		Path candidate = realPath(Path.of(requestedPath));
		if (isWithinAllowedRoot(candidate)) {
			return candidate;
		}
		throw new SecurityException("Access denied: path is outside the allowed roots: " + requestedPath);
	}

	private boolean isWithinAllowedRoot(Path candidate) {
		return allowedRoots.stream().anyMatch(candidate::startsWith);
	}

	private List<Path> resolveRoots(String configuredRoots) {
		if (configuredRoots == null || configuredRoots.isBlank()) {
			return List.of(realPath(Path.of(System.getProperty("user.dir"))));
		}
		return Arrays.stream(configuredRoots.split(File.pathSeparator))
				.map(String::trim)
				.filter(root -> !root.isEmpty())
				.map(Path::of)
				.map(this::realPath)
				.toList();
	}

	private Path realPath(Path path) {
		try {
			return path.toRealPath();
		} catch (IOException e) {
			throw new UncheckedIOException("Path does not exist or is not accessible: " + path, e);
		}
	}
}
