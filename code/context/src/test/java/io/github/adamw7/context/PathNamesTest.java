package io.github.adamw7.context;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.File;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;

/**
 * The name a path is recognised, loaded and ordered by. A project walked from a
 * filesystem root, or a listing that reaches one, meets a path {@link Path#getFileName}
 * answers {@code null} for — so the lookup every such walk goes through has to be total,
 * or the walk fails on an NPE rather than simply finding no sources there.
 */
class PathNamesTest {

	@Test
	void aPathIsNamedByItsLastElement() {
		assertEquals("Finder.java", PathNames.of(Path.of("src", "main", "java", "Finder.java")));
	}

	@Test
	void aBareNameIsItsOwnName() {
		assertEquals("Finder.java", PathNames.of(Path.of("Finder.java")));
	}

	@Test
	void aPathThatNamesNoFileIsNamedByThePathItself() {
		Path root = Path.of(File.separator);

		assertEquals(root.toString(), PathNames.of(root));
	}
}
