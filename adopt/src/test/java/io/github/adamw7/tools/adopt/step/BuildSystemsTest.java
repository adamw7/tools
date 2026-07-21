package io.github.adamw7.tools.adopt.step;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class BuildSystemsTest {

	@Test
	void detectsMavenFromPom(@TempDir Path directory) throws IOException {
		Files.writeString(directory.resolve("pom.xml"), "<project/>");
		Optional<BuildSystem> detected = BuildSystems.detect(BuildSystems.DEFAULTS, directory);
		assertEquals("maven", detected.orElseThrow().name());
	}

	@Test
	void detectsGradleFromGroovyBuildFile(@TempDir Path directory) throws IOException {
		Files.writeString(directory.resolve("build.gradle"), "plugins { id 'java' }\n");
		Optional<BuildSystem> detected = BuildSystems.detect(BuildSystems.DEFAULTS, directory);
		assertEquals("gradle", detected.orElseThrow().name());
	}

	@Test
	void detectsGradleFromKotlinBuildFile(@TempDir Path directory) throws IOException {
		Files.writeString(directory.resolve("build.gradle.kts"), "plugins { java }\n");
		Optional<BuildSystem> detected = BuildSystems.detect(BuildSystems.DEFAULTS, directory);
		assertEquals("gradle", detected.orElseThrow().name());
	}

	@Test
	void prefersMavenWhenBothBuildFilesArePresent(@TempDir Path directory) throws IOException {
		Files.writeString(directory.resolve("pom.xml"), "<project/>");
		Files.writeString(directory.resolve("build.gradle"), "plugins { id 'java' }\n");
		Optional<BuildSystem> detected = BuildSystems.detect(BuildSystems.DEFAULTS, directory);
		assertEquals("maven", detected.orElseThrow().name());
	}

	@Test
	void fallsBackToTheGitHubActionsGuardForAnUnsupportedProject(@TempDir Path directory) {
		Optional<BuildSystem> detected = BuildSystems.detect(BuildSystems.DEFAULTS, directory);
		assertEquals("github-actions", detected.orElseThrow().name());
	}

	@Test
	void detectsNothingWhenNoCandidateMatchesAndThereIsNoFallback(@TempDir Path directory) {
		List<BuildSystem> withoutFallback = List.of(new MavenBuildSystem(), new GradleBuildSystem());
		assertTrue(BuildSystems.detect(withoutFallback, directory).isEmpty());
	}

	@Test
	void listsBuildSystemNames() {
		assertEquals("maven/gradle/github-actions", BuildSystems.names(BuildSystems.DEFAULTS));
	}

	@Test
	void fallbackVerifyCommandRunsTheGuardScript() {
		assertEquals(List.of("sh", ".github/claude-md-guard.sh"), new FallbackBuildSystem().verifyCommand());
	}

	@Test
	void mavenVerifyCommandIsANonRecursiveValidate() {
		assertEquals(List.of("mvn", "-q", "-N", "validate"), new MavenBuildSystem().verifyCommand());
	}

	@Test
	void gradleVerifyCommandRunsTheGuardTask() {
		assertEquals(List.of("gradle", "-q", "enforceClaudeMd"), new GradleBuildSystem().verifyCommand());
	}
}
