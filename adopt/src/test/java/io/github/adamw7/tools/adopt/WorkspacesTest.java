package io.github.adamw7.tools.adopt;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.UncheckedIOException;
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

	/**
	 * The clone has nowhere to go if the workspace cannot be created, so the
	 * failure must surface rather than leaving a later step to fail obscurely.
	 */
	@Test
	void reportsAWorkspaceThatCannotBeCreated(@TempDir Path dir) throws IOException {
		Path blocker = dir.resolve("not-a-directory");
		Files.writeString(blocker, "");
		assertThrows(UncheckedIOException.class, () -> Workspaces.createIfMissing(blocker.resolve("workspace")));
	}

	@Test
	void createsTemporaryDirectoriesThatDoNotCollide() {
		Path first = Workspaces.createTemporary();
		Path second = Workspaces.createTemporary();
		assertNotEquals(first, second, "each run must get its own workspace");
		assertTrue(first.isAbsolute(), "the clone target is resolved against the workspace, so it must be absolute");
	}

	@Test
	void createsATemporaryDirectory() {
		Path workspace = Workspaces.createTemporary();
		assertTrue(Files.isDirectory(workspace));
		assertTrue(workspace.getFileName().toString().startsWith("claude-adopt-"));
	}
}
