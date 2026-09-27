package io.github.adamw7.tools.path;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.List;

/**
 * A set of root directories, and the check that a path lies inside one of them once
 * its symlinks are followed (path traversal, CWE-22). Both confinements in this
 * repository rest on it: the data module's {@code AllowedPaths}, handed to a file
 * source, and the context MCP server's allowed roots. One reading of where a path
 * really leads is the point, since a second reading is a second chance for a symlink
 * to lead out.
 *
 * <p>It ships in {@code mcp-common}, the lowest module the data and context modules
 * share, but outside {@code io.github.adamw7.tools.mcp}: confining a path is not MCP
 * scaffolding, and the data sources that confine through it are not MCP code.
 */
public final class PathBoundary {

	private final List<Path> roots;

	private PathBoundary(List<Path> roots) {
		this.roots = roots;
	}

	/**
	 * @param roots the directories to confine to, each held by its real path
	 * @throws UncheckedIOException when a root does not exist or cannot be resolved
	 */
	public static PathBoundary under(Collection<Path> roots) {
		return new PathBoundary(roots.stream().map(PathBoundary::realRoot).toList());
	}

	/** The roots, each by its real path. */
	public List<Path> roots() {
		return roots;
	}

	/**
	 * Whether the real location of {@code path} lies under one of the roots. A
	 * boundary with no roots contains nothing.
	 *
	 * @throws UncheckedIOException when the part of {@code path} that exists cannot be
	 *                              resolved
	 */
	public boolean contains(Path path) {
		Path real = realLocation(path);
		return roots.stream().anyMatch(real::startsWith);
	}

	/**
	 * The real, symlink-followed location of {@code path}, which need not exist yet: the
	 * nearest existing ancestor is resolved with {@link Path#toRealPath} and the
	 * not-yet-created remainder appended. A purely textual {@code normalize()} would
	 * collapse {@code ..} without ever following a link, letting
	 * {@code <root>/link/secret} slip past when {@code link} targets {@code /etc}.
	 *
	 * @throws UncheckedIOException when the part of {@code path} that exists cannot be
	 *                              resolved
	 */
	public static Path realLocation(Path path) {
		Path absolute = path.toAbsolutePath().normalize();
		Path existing = absolute;
		while (existing != null && !Files.exists(existing)) {
			existing = existing.getParent();
		}
		if (existing == null) {
			return absolute;
		}
		try {
			return existing.toRealPath().resolve(existing.relativize(absolute)).normalize();
		} catch (IOException e) {
			throw new UncheckedIOException("Could not resolve real path for: " + path, e);
		}
	}

	private static Path realRoot(Path root) {
		try {
			return root.toRealPath();
		} catch (IOException e) {
			throw new UncheckedIOException("Path does not exist or is not accessible: " + root, e);
		}
	}
}
