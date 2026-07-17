package io.github.adamw7.tools.adopt;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class MainTest {

	private static final String REPO_URL = "https://github.com/owner/repo.git";

	@Test
	void rejectsMissingArguments() {
		IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
				() -> Main.main(new String[0]));
		assertTrue(exception.getMessage().contains("Usage"), exception.getMessage());
	}

	@Test
	void rejectsNullArguments() {
		assertThrows(IllegalArgumentException.class, () -> Main.main(null));
	}

	@Test
	void rejectsBlankRepositoryUrl() {
		assertThrows(IllegalArgumentException.class, () -> Main.main(new String[] { "   " }));
	}

	@Test
	void createsSuppliedWorkspaceDirectoryWhenMissing(@TempDir Path dir) {
		Path workspace = dir.resolve("nested/workspace");
		Path resolved = Main.workspace(new String[] { REPO_URL, workspace.toString() });
		assertEquals(workspace, resolved);
		assertTrue(Files.isDirectory(workspace));
	}

	@Test
	void keepsAnExistingSuppliedWorkspaceDirectory(@TempDir Path dir) {
		Path resolved = Main.workspace(new String[] { REPO_URL, dir.toString() });
		assertEquals(dir, resolved);
		assertTrue(Files.isDirectory(dir));
	}

	@Test
	void createsTemporaryWorkspaceWhenNoneSupplied() {
		Path resolved = Main.workspace(new String[] { REPO_URL });
		assertTrue(Files.isDirectory(resolved));
	}
}
