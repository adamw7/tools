package io.github.adamw7.tools.adopt.step;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import io.github.adamw7.tools.adopt.AdoptionException;

class AssetInstallerTest {

	@Test
	void writesTheAssetCreatingParentDirectories(@TempDir Path checkout) throws IOException {
		AssetInstaller installer = new AssetInstaller("a/b/file.txt", "content\n");
		assertTrue(installer.install(checkout));
		assertEquals("content\n", Files.readString(checkout.resolve("a/b/file.txt")));
	}

	@Test
	void neverOverwritesAnExistingFile(@TempDir Path checkout) throws IOException {
		Files.writeString(checkout.resolve("file.txt"), "the project's own version\n");
		AssetInstaller installer = new AssetInstaller("file.txt", "starter content\n");
		assertFalse(installer.install(checkout));
		assertEquals("the project's own version\n", Files.readString(checkout.resolve("file.txt")));
	}

	@Test
	void marksTheAssetExecutableWhenAsked(@TempDir Path checkout) {
		AssetInstaller installer = new AssetInstaller("hook.sh", "#!/bin/sh\n", true);
		assertTrue(installer.install(checkout));
		assertTrue(Files.isExecutable(checkout.resolve("hook.sh")));
	}

	@Test
	void doesNotMarkAnOrdinaryAssetExecutable(@TempDir Path checkout) {
		AssetInstaller installer = new AssetInstaller("plain.txt", "content\n");
		assertTrue(installer.install(checkout));
		assertFalse(Files.isExecutable(checkout.resolve("plain.txt")));
	}

	@Test
	void exposesItsRelativePath() {
		assertEquals("a/file.txt", new AssetInstaller("a/file.txt", "content\n").relativePath());
	}

	@Test
	void failsWithAdoptionExceptionWhenTheAssetCannotBeWritten(@TempDir Path checkout) throws IOException {
		Files.createFile(checkout.resolve("blocking"));
		AssetInstaller installer = new AssetInstaller("blocking/file.txt", "content\n");
		assertThrows(AdoptionException.class, () -> installer.install(checkout));
	}
}
