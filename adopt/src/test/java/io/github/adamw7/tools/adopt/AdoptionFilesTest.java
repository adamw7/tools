package io.github.adamw7.tools.adopt;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class AdoptionFilesTest {

	@Test
	void readsAFilesText(@TempDir Path directory) throws IOException {
		Path file = Files.writeString(directory.resolve("notes.txt"), "content\n");
		assertEquals("content\n", AdoptionFiles.read(file, "notes"));
	}

	@Test
	void namesTheFileAndItsDescriptionWhenAReadFails(@TempDir Path directory) {
		Path missing = directory.resolve("absent.txt");
		AdoptionException thrown = assertThrows(AdoptionException.class, () -> AdoptionFiles.read(missing, "POM"));
		assertTrue(thrown.getMessage().contains("POM"), thrown.getMessage());
		assertTrue(thrown.getMessage().contains(missing.toString()), thrown.getMessage());
	}

	@Test
	void writesTheContent(@TempDir Path directory) throws IOException {
		Path file = directory.resolve("report.json");
		AdoptionFiles.write(file, "{}\n", "the adoption report");
		assertEquals("{}\n", Files.readString(file));
	}

	/**
	 * The starter assets live under directories a checkout need not carry yet
	 * ({@code .claude/hooks/}), so the write has to create them rather than fail on
	 * the missing parent.
	 */
	@Test
	void createsMissingParentDirectories(@TempDir Path directory) throws IOException {
		Path file = directory.resolve("nested/deeper/hook.sh");
		AdoptionFiles.write(file, "exit 0\n", "asset file");
		assertEquals("exit 0\n", Files.readString(file));
	}

	@Test
	void namesTheFileAndItsDescriptionWhenAWriteFails(@TempDir Path directory) throws IOException {
		Path file = directory.resolve("occupied");
		Files.createDirectory(file);
		AdoptionException thrown = assertThrows(AdoptionException.class,
				() -> AdoptionFiles.write(file, "content", "workflow file"));
		assertTrue(thrown.getMessage().contains("workflow file"), thrown.getMessage());
		assertTrue(thrown.getMessage().contains(file.toString()), thrown.getMessage());
	}
}
