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

class GradleBuildSystemTest {

	private final GradleBuildSystem buildSystem = new GradleBuildSystem();

	@Test
	void matchesNothingWithoutABuildScript(@TempDir Path directory) {
		assertFalse(buildSystem.matches(directory));
	}

	/**
	 * Detection and installation are separate calls, so a checkout that lost its
	 * build script in between must fail with a message naming the directory rather
	 * than with an empty {@code Optional} unwrapping somewhere deeper.
	 */
	@Test
	void installingWithoutABuildScriptAbortsAdoption(@TempDir Path directory) {
		assertThrows(AdoptionException.class, () -> buildSystem.install(directory));
	}

	/**
	 * Gradle itself prefers the Groovy script when a project carries both, so the
	 * guard has to land in the file Gradle will actually run.
	 */
	@Test
	void guardsTheGroovyScriptWhenTheCheckoutCarriesBoth(@TempDir Path directory) throws IOException {
		Path groovy = directory.resolve(GradleBuildSystem.GROOVY_BUILD_FILE);
		Path kotlin = directory.resolve(GradleBuildSystem.KOTLIN_BUILD_FILE);
		Files.writeString(groovy, "plugins { id 'java' }\n");
		Files.writeString(kotlin, "plugins { java }\n");

		assertTrue(buildSystem.install(directory));

		assertTrue(Files.readString(groovy).contains(GradleGuardInstaller.GUARD_TASK),
				"the guard belongs in the script Gradle resolves first");
		assertFalse(Files.readString(kotlin).contains(GradleGuardInstaller.GUARD_TASK),
				"the unused Kotlin script must be left alone");
	}

	@Test
	void guardsTheKotlinScriptWhenItIsTheOnlyOne(@TempDir Path directory) throws IOException {
		Path kotlin = directory.resolve(GradleBuildSystem.KOTLIN_BUILD_FILE);
		Files.writeString(kotlin, "plugins { java }\n");

		assertTrue(buildSystem.install(directory));

		assertTrue(Files.readString(kotlin).contains("tasks.register(\"" + GradleGuardInstaller.GUARD_TASK + "\")"),
				"the Kotlin DSL syntax must be used for a .kts script");
	}

	@Test
	void reportsAnAlreadyGuardedScriptAsUnchanged(@TempDir Path directory) throws IOException {
		Files.writeString(directory.resolve(GradleBuildSystem.GROOVY_BUILD_FILE), "plugins { id 'java' }\n");
		assertTrue(buildSystem.install(directory));
		assertFalse(buildSystem.install(directory), "a second adoption must leave the script unchanged");
	}

	@Test
	void isNamedGradle() {
		assertEquals("gradle", buildSystem.name());
	}
}
