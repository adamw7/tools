package io.github.adamw7.tools.adopt;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Creates the workspace directory an adoption clones into: either the directory
 * the caller named — created when it does not yet exist, so the clone step
 * always has a directory to run in — or a fresh temporary one when the caller
 * left the choice open. Shared by the command-line entry point and the MCP
 * server, so both resolve workspaces identically.
 *
 * <p>The returned path is always absolute. The clone step runs {@code git clone}
 * with the workspace as its working directory and the checkout directory
 * ({@code workspace/name}) as the clone target, so a relative workspace would
 * make git resolve that target against the working directory a second time and
 * nest the checkout under {@code workspace/workspace/name}, leaving every later
 * step unable to find it. Absolutising the workspace up front — the temporary
 * directory already is — keeps both entry points on the same, unambiguous path.
 */
public final class Workspaces {

	private Workspaces() {
	}

	public static Path createIfMissing(Path workspace) {
		try {
			return Files.createDirectories(workspace).toAbsolutePath();
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}

	public static Path createTemporary() {
		try {
			return Files.createTempDirectory("claude-adopt-");
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}
}
