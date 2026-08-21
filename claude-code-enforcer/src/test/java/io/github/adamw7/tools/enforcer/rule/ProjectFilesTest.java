package io.github.adamw7.tools.enforcer.rule;

import static io.github.adamw7.tools.test.TestFiles.createDirectory;
import static io.github.adamw7.tools.test.TestFiles.writeString;
import static io.github.adamw7.tools.test.ExpectedFailures.assertFailure;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;

import org.apache.maven.enforcer.rule.api.EnforcerRuleException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ProjectFilesTest {

	@TempDir
	private Path tempDir;

	@Test
	void requireDirectoryPassesForAnExistingDirectory() {
		assertDoesNotThrow(() -> ProjectFiles.requireDirectory(tempDir.toFile(), "Commands"));
	}

	@Test
	void requireDirectoryFailsWithLabelWhenDirectoryIsMissing() {
		File absent = tempDir.resolve("absent").toFile();

		assertFailure(EnforcerRuleException.class, () -> ProjectFiles.requireDirectory(absent, "Agents"),
				"Agents directory does not exist at", absent.toString());
	}

	@Test
	void requireDirectoryFailsWhenPathIsAFileRatherThanADirectory() {
		File file = writeString(tempDir.resolve("review.md"), "body").toFile();

		assertFailure(EnforcerRuleException.class, () -> ProjectFiles.requireDirectory(file, "Skills"),
				"Skills directory does not exist at");
	}

	@Test
	void requireDirectoryOrAbsentAcceptsAnAbsentDirectory() {
		assertDoesNotThrow(
				() -> ProjectFiles.requireDirectoryOrAbsent(tempDir.resolve("hooks").toFile(), "Hooks"));
	}

	@Test
	void requireDirectoryOrAbsentAcceptsADirectoryThatIsThere() {
		File directory = createDirectory(tempDir.resolve("hooks")).toFile();

		assertDoesNotThrow(() -> ProjectFiles.requireDirectoryOrAbsent(directory, "Hooks"));
	}

	@Test
	void requireDirectoryOrAbsentRejectsAPathThatIsAFile() {
		// Such a path lists nothing, so the rule reading it scanned nothing and
		// reported nothing — which reads exactly like a project with no hooks.
		File file = writeString(tempDir.resolve("hooks"), "#!/bin/sh").toFile();

		assertFailure(EnforcerRuleException.class,
				() -> ProjectFiles.requireDirectoryOrAbsent(file, "Hooks"), "is not a directory");
	}

	@Test
	void markdownFilesInReturnsOnlyMarkdownFiles() {
		writeString(tempDir.resolve("review.md"), "body");
		writeString(tempDir.resolve("commit.md"), "body");
		writeString(tempDir.resolve("notes.txt"), "ignored");

		assertEquals(List.of("commit.md", "review.md"), sortedNames(ProjectFiles.markdownFilesIn(tempDir.toFile())));
	}

	@Test
	void markdownFilesInExcludesSubdirectoriesEvenWhenNamedLikeMarkdown() {
		createDirectory(tempDir.resolve("nested.md"));
		writeString(tempDir.resolve("real.md"), "body");

		assertEquals(List.of("real.md"), sortedNames(ProjectFiles.markdownFilesIn(tempDir.toFile())));
	}

	@Test
	void markdownFilesInReturnsResultsSortedByName() {
		writeString(tempDir.resolve("review.md"), "body");
		writeString(tempDir.resolve("apply.md"), "body");
		writeString(tempDir.resolve("commit.md"), "body");

		assertEquals(List.of("apply.md", "commit.md", "review.md"), names(ProjectFiles.markdownFilesIn(tempDir.toFile())));
	}

	@Test
	void markdownFilesInReturnsEmptyListForAnEmptyDirectory() {
		assertEquals(List.of(), ProjectFiles.markdownFilesIn(tempDir.toFile()));
	}

	@Test
	void markdownFilesInReturnsEmptyListWhenPathIsNotAListableDirectory() {
		File file = writeString(tempDir.resolve("review.md"), "body").toFile();

		assertEquals(List.of(), ProjectFiles.markdownFilesIn(file));
	}

	@Test
	void subdirectoriesOfReturnsOnlyDirectories() {
		createDirectory(tempDir.resolve("commands"));
		createDirectory(tempDir.resolve("agents"));
		writeString(tempDir.resolve("review.md"), "body");

		assertEquals(List.of("agents", "commands"), sortedNames(ProjectFiles.subdirectoriesOf(tempDir.toFile())));
	}

	@Test
	void subdirectoriesOfReturnsResultsSortedByName() {
		createDirectory(tempDir.resolve("skills"));
		createDirectory(tempDir.resolve("agents"));
		createDirectory(tempDir.resolve("commands"));

		assertEquals(List.of("agents", "commands", "skills"), names(ProjectFiles.subdirectoriesOf(tempDir.toFile())));
	}

	@Test
	void subdirectoriesOfReturnsEmptyListForAnEmptyDirectory() {
		assertEquals(List.of(), ProjectFiles.subdirectoriesOf(tempDir.toFile()));
	}

	@Test
	void subdirectoriesOfReturnsEmptyListWhenPathIsNotAListableDirectory() {
		File file = writeString(tempDir.resolve("review.md"), "body").toFile();

		assertEquals(List.of(), ProjectFiles.subdirectoriesOf(file));
	}

	@Test
	void isMarkdownAcceptsAMarkdownPath() {
		assertTrue(ProjectFiles.isMarkdown(tempDir.resolve("CLAUDE.md")));
	}

	@Test
	void isMarkdownRejectsAPathWithAnotherExtension() {
		assertFalse(ProjectFiles.isMarkdown(tempDir.resolve("settings.json")));
	}

	/**
	 * A path with no name element — a filesystem root, which {@link Path#getFileName}
	 * answers {@code null} for — is no Markdown document, and saying so is what keeps a
	 * rule scanning a tree that reaches one from failing on an NPE instead of a verdict.
	 */
	@Test
	void isMarkdownRejectsAPathThatNamesNoFile() {
		assertFalse(ProjectFiles.isMarkdown(Path.of(File.separator)));
	}

	@Test
	void markdownBaseNameStripsTheMarkdownSuffix() {
		assertEquals("git-commit", ProjectFiles.markdownBaseName(tempDir.resolve("git-commit.md").toFile()));
	}

	@Test
	void markdownBaseNameStripsOnlyTheTrailingSuffix() {
		assertEquals("notes.md.backup", ProjectFiles.markdownBaseName(tempDir.resolve("notes.md.backup.md").toFile()));
	}

	@Test
	void normalizedResolvesDotSegmentsToOneAbsolutePath() {
		File roundabout = tempDir.resolve("nested").resolve("..").resolve("CLAUDE.md").toFile();

		assertEquals(ProjectFiles.normalized(tempDir.resolve("CLAUDE.md").toFile()),
				ProjectFiles.normalized(roundabout));
	}

	@Test
	void normalizedMakesARelativePathAbsolute() {
		assertTrue(ProjectFiles.normalized(new File("CLAUDE.md")).isAbsolute());
	}

	private static List<String> sortedNames(List<File> files) {
		return names(files).stream().sorted(Comparator.naturalOrder()).toList();
	}

	/** Names in the list's own order, so a test can assert the helper's sorting rather than re-sort it. */
	private static List<String> names(List<File> files) {
		return files.stream().map(File::getName).toList();
	}
}
