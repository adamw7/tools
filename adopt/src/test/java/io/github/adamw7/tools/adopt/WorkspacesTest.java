package io.github.adamw7.tools.adopt;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class WorkspacesTest {

	@Test
	void createsAMissingDirectoryIncludingParents(@TempDir Path dir) {
		Path workspace = dir.resolve("a/b/c");
		assertEquals(workspace, Workspaces.createIfMissing(workspace));
		assertTrue(Files.isDirectory(workspace));
	}

	@Test
	void keepsAnExistingDirectory(@TempDir Path dir) {
		assertEquals(dir, Workspaces.createIfMissing(dir));
		assertTrue(Files.isDirectory(dir));
	}

	@Test
	void absolutisesARelativeWorkspaceSoTheCloneTargetIsNotNested(@TempDir Path dir) {
		Path relative = dir.getFileSystem().getPath("relative-workspace-" + System.nanoTime());
		try {
			Path created = Workspaces.createIfMissing(relative);
			assertTrue(created.isAbsolute());
			assertEquals(relative.toAbsolutePath(), created);
			assertTrue(Files.isDirectory(created));
		} finally {
			deleteQuietly(relative);
		}
	}

	private void deleteQuietly(Path path) {
		try {
			Files.deleteIfExists(path);
		} catch (IOException ignored) {
			path.toFile().deleteOnExit();
		}
	}

	@Test
	void createsATemporaryDirectory() {
		Path workspace = Workspaces.createTemporary();
		assertTrue(Files.isDirectory(workspace));
		assertTrue(workspace.getFileName().toString().startsWith("claude-adopt-"));
	}
}
