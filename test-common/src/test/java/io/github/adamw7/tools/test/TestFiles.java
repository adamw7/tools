package io.github.adamw7.tools.test;

import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * The file-system fixtures every rule test builds: nearly all of them lay out a
 * temporary directory of documents and then assert on what a rule says about it.
 * Each operation wraps its {@link IOException} in an {@link UncheckedIOException}
 * so a fixture can be written inline, without a {@code throws} clause on the test
 * method or a {@code try} block that has nothing to do with what is being
 * asserted.
 * <p>
 * A failure here is a broken fixture, never the behaviour under test, which is
 * why it is thrown unchecked rather than reported: the test that follows would
 * have nothing meaningful to assert.
 */
public final class TestFiles {

	private TestFiles() {
	}

	/** Writes {@code content} to {@code file}, creating its parent directories first. */
	public static Path writeString(Path file, String content) {
		try {
			Files.createDirectories(file.getParent());
			return Files.writeString(file, content);
		} catch (IOException e) {
			throw new UncheckedIOException("Could not write " + file, e);
		}
	}

	/** Writes raw bytes, for a fixture that must not be decodable as text. */
	public static Path writeBytes(Path file, byte[] content) {
		try {
			Files.createDirectories(file.getParent());
			return Files.write(file, content);
		} catch (IOException e) {
			throw new UncheckedIOException("Could not write " + file, e);
		}
	}

	public static String readString(Path file) {
		try {
			return Files.readString(file);
		} catch (IOException e) {
			throw new UncheckedIOException("Could not read " + file, e);
		}
	}

	public static String readString(File file) {
		return readString(file.toPath());
	}

	/** Creates {@code dir} and any missing parent, and returns it so a fixture reads as one expression. */
	public static Path createDirectory(Path dir) {
		try {
			return Files.createDirectories(dir);
		} catch (IOException e) {
			throw new UncheckedIOException("Could not create " + dir, e);
		}
	}

	/**
	 * Links {@code link} to {@code target}, or skips the calling test where the
	 * filesystem has no symbolic links — Windows without developer mode. Unlike the
	 * other fixtures this one aborts rather than fails, because a filesystem that
	 * cannot express the fixture says nothing about the rule under test.
	 */
	public static void assumeSymlink(Path link, Path target) {
		try {
			Files.createSymbolicLink(link, target);
		} catch (IOException | UnsupportedOperationException e) {
			assumeTrue(false, "filesystem does not support symbolic links");
		}
	}
}
