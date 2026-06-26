package io.github.adamw7.tools.enforcer.definition;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Comparator;

import org.apache.maven.enforcer.rule.api.EnforcerRuleException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class DefinitionFilesTest {

	@TempDir
	private Path tempDir;

	@Test
	void verifyDirectoryPassesForAnExistingDirectory() {
		assertDoesNotThrow(() -> DefinitionFiles.verifyDirectory(tempDir.toFile(), "Commands"));
	}

	@Test
	void verifyDirectoryFailsWithLabelWhenDirectoryIsMissing() {
		File absent = tempDir.resolve("absent").toFile();

		EnforcerRuleException exception = assertThrows(EnforcerRuleException.class,
				() -> DefinitionFiles.verifyDirectory(absent, "Agents"));
		assertTrue(exception.getMessage().contains("Agents directory does not exist at"), exception.getMessage());
		assertTrue(exception.getMessage().contains(absent.toString()), exception.getMessage());
	}

	@Test
	void verifyDirectoryFailsWhenPathIsAFileRatherThanADirectory() {
		File file = writeString(tempDir.resolve("review.md"), "body");

		EnforcerRuleException exception = assertThrows(EnforcerRuleException.class,
				() -> DefinitionFiles.verifyDirectory(file, "Skills"));
		assertTrue(exception.getMessage().contains("Skills directory does not exist at"), exception.getMessage());
	}

	@Test
	void markdownFilesReturnsOnlyMarkdownFiles() {
		writeString(tempDir.resolve("review.md"), "body");
		writeString(tempDir.resolve("commit.md"), "body");
		writeString(tempDir.resolve("notes.txt"), "ignored");

		assertArrayEquals(new String[] { "commit.md", "review.md" }, sortedNames(DefinitionFiles.markdownFiles(tempDir.toFile())));
	}

	@Test
	void markdownFilesExcludesSubdirectoriesEvenWhenNamedLikeMarkdown() {
		createDirectory(tempDir.resolve("nested.md"));
		writeString(tempDir.resolve("real.md"), "body");

		assertArrayEquals(new String[] { "real.md" }, sortedNames(DefinitionFiles.markdownFiles(tempDir.toFile())));
	}

	@Test
	void markdownFilesReturnsEmptyArrayForAnEmptyDirectory() {
		assertEquals(0, DefinitionFiles.markdownFiles(tempDir.toFile()).length);
	}

	@Test
	void markdownFilesReturnsEmptyArrayWhenPathIsNotAListableDirectory() {
		File file = writeString(tempDir.resolve("review.md"), "body");

		assertArrayEquals(new File[0], DefinitionFiles.markdownFiles(file));
	}

	@Test
	void subdirectoriesReturnsOnlyDirectories() {
		createDirectory(tempDir.resolve("commands"));
		createDirectory(tempDir.resolve("agents"));
		writeString(tempDir.resolve("review.md"), "body");

		assertArrayEquals(new String[] { "agents", "commands" }, sortedNames(DefinitionFiles.subdirectories(tempDir.toFile())));
	}

	@Test
	void subdirectoriesReturnsEmptyArrayForAnEmptyDirectory() {
		assertEquals(0, DefinitionFiles.subdirectories(tempDir.toFile()).length);
	}

	@Test
	void subdirectoriesReturnsEmptyArrayWhenPathIsNotAListableDirectory() {
		File file = writeString(tempDir.resolve("review.md"), "body");

		assertArrayEquals(new File[0], DefinitionFiles.subdirectories(file));
	}

	@Test
	void baseNameStripsTheMarkdownSuffix() {
		assertEquals("git-commit", DefinitionFiles.baseName(tempDir.resolve("git-commit.md").toFile()));
	}

	@Test
	void baseNameStripsOnlyTheTrailingSuffix() {
		assertEquals("notes.md.backup", DefinitionFiles.baseName(tempDir.resolve("notes.md.backup.md").toFile()));
	}

	private static String[] sortedNames(File[] files) {
		return Arrays.stream(files).map(File::getName).sorted(Comparator.naturalOrder()).toArray(String[]::new);
	}

	private static File writeString(Path file, String content) {
		try {
			return Files.writeString(file, content).toFile();
		} catch (IOException e) {
			throw new UncheckedIOException("Could not write " + file, e);
		}
	}

	private static void createDirectory(Path dir) {
		try {
			Files.createDirectory(dir);
		} catch (IOException e) {
			throw new UncheckedIOException("Could not create " + dir, e);
		}
	}
}
