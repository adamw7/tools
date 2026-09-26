package io.github.adamw7.tools.path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class PathBoundaryTest {

	@TempDir
	Path directory;

	@Test
	void containsAPathUnderARootWhetherOrNotItExists() throws IOException {
		Path root = Files.createDirectory(directory.resolve("root"));
		PathBoundary boundary = PathBoundary.under(List.of(root));

		assertTrue(boundary.contains(root));
		assertTrue(boundary.contains(root.resolve("not-yet/written.csv")));
	}

	@Test
	void doesNotContainAPathThatClimbsOut() throws IOException {
		Path root = Files.createDirectory(directory.resolve("root"));
		PathBoundary boundary = PathBoundary.under(List.of(root));

		assertFalse(boundary.contains(root.resolve("../elsewhere.csv")));
		assertFalse(boundary.contains(directory));
	}

	@Test
	void containsAPathUnderAnyOfSeveralRoots() throws IOException {
		Path first = Files.createDirectory(directory.resolve("first"));
		Path second = Files.createDirectory(directory.resolve("second"));
		PathBoundary boundary = PathBoundary.under(List.of(first, second));

		assertTrue(boundary.contains(second.resolve("data.csv")));
		assertEquals(List.of(first.toRealPath(), second.toRealPath()), boundary.roots());
	}

	@Test
	void containsNothingWithoutRoots() {
		assertFalse(PathBoundary.under(List.of()).contains(directory));
	}

	@Test
	void refusesARootThatDoesNotExist() {
		assertThrows(UncheckedIOException.class, () -> PathBoundary.under(List.of(directory.resolve("missing"))));
	}

	/** The link sits inside the root by its name, and leads out of it. */
	@Test
	void followsASymlinkOutOfTheRoot() throws IOException {
		Path root = Files.createDirectory(directory.resolve("root"));
		Path outside = Files.createDirectory(directory.resolve("outside"));
		Path link = root.resolve("link");
		assumeTrue(canCreateSymbolicLink(link, outside), "symbolic links are not available here");

		assertFalse(PathBoundary.under(List.of(root)).contains(link.resolve("secret.csv")));
		assertEquals(outside.toRealPath().resolve("secret.csv"), PathBoundary.realLocation(link.resolve("secret.csv")));
	}

	private static boolean canCreateSymbolicLink(Path link, Path target) {
		try {
			Files.createSymbolicLink(link, target);
			return true;
		} catch (UnsupportedOperationException | IOException e) {
			return false;
		}
	}
}
