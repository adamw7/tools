package io.github.adamw7.tools.adopt.step;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class GradleGuardInstallerTest {

	private final GradleGuardInstaller installer = new GradleGuardInstaller();

	@Test
	void appendsGroovyGuardToBuildGradle(@TempDir Path directory) throws IOException {
		Path buildFile = directory.resolve("build.gradle");
		Files.writeString(buildFile, "plugins { id 'java' }\n");
		assertTrue(installer.install(buildFile));
		String content = Files.readString(buildFile);
		assertTrue(content.contains("tasks.register('enforceClaudeMd')"));
		assertTrue(content.contains("throw new GradleException('CLAUDE.md is missing or empty')"));
		assertTrue(content.startsWith("plugins { id 'java' }"));
	}

	@Test
	void appendsKotlinGuardToBuildGradleKts(@TempDir Path directory) throws IOException {
		Path buildFile = directory.resolve("build.gradle.kts");
		Files.writeString(buildFile, "plugins { java }\n");
		assertTrue(installer.install(buildFile));
		String content = Files.readString(buildFile);
		assertTrue(content.contains("tasks.register(\"enforceClaudeMd\")"));
		assertTrue(content.contains("val claudeMd = file(\"$projectDir/CLAUDE.md\")"));
	}

	@Test
	void leavesAnAlreadyGuardedBuildUnchanged(@TempDir Path directory) throws IOException {
		Path buildFile = directory.resolve("build.gradle");
		Files.writeString(buildFile, "plugins { id 'java' }\n");
		installer.install(buildFile);
		String afterFirstInstall = Files.readString(buildFile);
		assertFalse(installer.install(buildFile));
		assertEquals(afterFirstInstall, Files.readString(buildFile));
	}
}
