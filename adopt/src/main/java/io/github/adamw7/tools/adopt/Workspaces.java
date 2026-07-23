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
 */
public final class Workspaces {

	private Workspaces() {
	}

	public static Path createIfMissing(Path workspace) {
		try {
			return Files.createDirectories(workspace);
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
