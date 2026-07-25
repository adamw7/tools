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
	void matchesCarriageReturnLineEndingsWhenAppendingTheGuard(@TempDir Path directory) throws IOException {
		Path buildFile = directory.resolve("build.gradle");
		Files.writeString(buildFile, "plugins { id 'java' }\r\n");
		assertTrue(installer.install(buildFile));
		String content = Files.readString(buildFile);
		assertTrue(content.contains("tasks.register('enforceClaudeMd')"), "the guard must be appended");
		assertFalse(content.replace("\r\n", "").contains("\n"),
				"a CRLF build script must stay CRLF; the appended block must not introduce bare LF endings");
	}

	@Test
	void unreadableBuildScriptAbortsAdoption(@TempDir Path directory) throws IOException {
		Path buildFile = directory.resolve("build.gradle");
		Files.createDirectory(buildFile);
		assertThrows(AdoptionException.class, () -> installer.install(buildFile));
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

	/**
	 * Matching the bare task name anywhere in the script let a comment stand in for
	 * the task itself, so the guard was never installed and the adoption then failed
	 * running a verification task that did not exist.
	 */
	@Test
	void installsTheGuardWhenTheScriptOnlyMentionsTheTaskInAComment(@TempDir Path directory) throws IOException {
		Path buildFile = directory.resolve("build.gradle");
		Files.writeString(buildFile, "// TODO: one day add an enforceClaudeMd task\nplugins { id 'java' }\n");
		assertTrue(installer.install(buildFile));
		assertTrue(Files.readString(buildFile).contains("tasks.register('enforceClaudeMd')"));
	}

	@Test
	void installsTheGuardWhenAnEarlierDeclarationWasCommentedOut(@TempDir Path directory) throws IOException {
		Path buildFile = directory.resolve("build.gradle.kts");
		Files.writeString(buildFile, "// tasks.register(\"enforceClaudeMd\") { }\nplugins { java }\n");
		assertTrue(installer.install(buildFile));
		assertTrue(Files.readString(buildFile).contains("tasks.register(\"enforceClaudeMd\")"));
	}

	/**
	 * A project that registers the task itself keeps its own version: appending a
	 * second registration of the same name would fail the Gradle build outright.
	 */
	@Test
	void leavesAScriptThatRegistersTheTaskItselfUnchanged(@TempDir Path directory) throws IOException {
		Path buildFile = directory.resolve("build.gradle");
		String own = "tasks.register('enforceClaudeMd') {\n    doLast { }\n}\n";
		Files.writeString(buildFile, own);
		assertFalse(installer.install(buildFile));
		assertEquals(own, Files.readString(buildFile));
	}

	@Test
	void recognisesARegistrationSpreadOverSeveralLines(@TempDir Path directory) throws IOException {
		Path buildFile = directory.resolve("build.gradle");
		String own = "tasks.register(\n    'enforceClaudeMd'\n) {\n    doLast { }\n}\n";
		Files.writeString(buildFile, own);
		assertFalse(installer.install(buildFile));
		assertEquals(own, Files.readString(buildFile));
	}
}
