package io.github.adamw7.context.mcp;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

public class PathPolicyTest {

	@TempDir
	Path allowedRoot;

	@TempDir
	Path otherRoot;

	@Test
	void resolvesAPathInsideAnAllowedRoot() throws IOException {
		Path nested = Files.createDirectory(allowedRoot.resolve("module"));

		Path resolved = new PathPolicy(allowedRoot.toString()).resolve(nested.toString());

		assertEquals(nested.toRealPath(), resolved);
	}

	@Test
	void resolvesTheAllowedRootItself() throws IOException {
		Path resolved = new PathPolicy(allowedRoot.toString()).resolve(allowedRoot.toString());

		assertEquals(allowedRoot.toRealPath(), resolved);
	}

	@Test
	void rejectsAPathOutsideEveryAllowedRoot() {
		PathPolicy policy = new PathPolicy(allowedRoot.toString());

		assertThrows(SecurityException.class, () -> policy.resolve(otherRoot.toString()));
	}

	@Test
	void rejectsTraversalThatEscapesTheAllowedRoot() {
		PathPolicy policy = new PathPolicy(allowedRoot.toString());
		String escaping = allowedRoot.resolve("..").toString();

		assertThrows(SecurityException.class, () -> policy.resolve(escaping));
	}

	@Test
	void rejectsAPathThatDoesNotExist() {
		PathPolicy policy = new PathPolicy(allowedRoot.toString());
		String missing = allowedRoot.resolve("does-not-exist").toString();

		assertThrows(UncheckedIOException.class, () -> policy.resolve(missing));
	}

	@Test
	void defaultsToTheWorkingDirectoryWhenNoRootsAreConfigured() throws IOException {
		Path workingDir = Path.of(System.getProperty("user.dir")).toRealPath();

		Path resolved = new PathPolicy("").resolve(workingDir.toString());

		assertEquals(workingDir, resolved);
	}

	@Test
	void supportsSeveralAllowedRoots() throws IOException {
		String roots = allowedRoot + java.io.File.pathSeparator + otherRoot;
		PathPolicy policy = new PathPolicy(roots);

		assertEquals(otherRoot.toRealPath(), policy.resolve(otherRoot.toString()));
	}
}
