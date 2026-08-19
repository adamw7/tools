package io.github.adamw7.tools.adopt;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

/**
 * Creates the workspace directory an adoption clones into: the directory the
 * caller named, created when it does not yet exist, or a fresh temporary one when
 * the caller left the choice open. Shared by the command line and the MCP server,
 * and failing with an {@link AdoptionException} like every other adoption failure.
 *
 * <p>The returned path is always absolute: the clone step runs {@code git clone}
 * with the workspace as its working directory and {@code workspace/name} as the
 * target, so a relative one would resolve twice and nest the checkout under
 * {@code workspace/workspace/name}.
 */
public final class Workspaces {

	private Workspaces() {
	}

	/**
	 * The one place the "named workspace or a temporary one" decision is made, so
	 * the command line and the MCP tool cannot drift apart on it.
	 *
	 * @param workspace the directory the caller named, or empty when it left the
	 *                  choice open
	 */
	public static Path resolve(Optional<Path> workspace) {
		return workspace.map(Workspaces::createIfMissing).orElseGet(Workspaces::createTemporary);
	}

	/**
	 * Resolves a workspace named as text, treating a blank name as "not supplied"
	 * the way {@link Text} does everywhere else the adoption reads an optional
	 * input, so an omitted MCP argument falls back to a temporary workspace instead
	 * of resolving the empty path.
	 */
	public static Path resolveNamed(String workspace) {
		return resolve(Text.isPresent(workspace) ? Optional.of(Path.of(workspace.strip())) : Optional.empty());
	}

	public static Path createIfMissing(Path workspace) {
		try {
			return Files.createDirectories(workspace).toAbsolutePath();
		} catch (IOException e) {
			throw new AdoptionException("Could not create the workspace directory: " + workspace, e);
		}
	}

	public static Path createTemporary() {
		try {
			return Files.createTempDirectory("claude-adopt-");
		} catch (IOException e) {
			throw new AdoptionException("Could not create a temporary workspace directory", e);
		}
	}
}
