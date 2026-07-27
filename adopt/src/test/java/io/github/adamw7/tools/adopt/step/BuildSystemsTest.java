package io.github.adamw7.tools.adopt.step;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import io.github.adamw7.tools.adopt.AdoptionException;

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
	void fallbackVerifyCommandRunsTheGuardScript(@TempDir Path directory) {
		assertEquals(List.of("sh", ".github/claude-md-guard.sh"), new FallbackBuildSystem().verifyCommand(directory));
	}

	@Test
	void mavenVerifyCommandIsANonRecursiveValidate(@TempDir Path directory) {
		assertEquals(List.of("mvn", "-q", "-N", "validate"), new MavenBuildSystem().verifyCommand(directory));
	}

	@Test
	void gradleVerifyCommandRunsTheGuardTask(@TempDir Path directory) {
		assertEquals(List.of("gradle", "-q", "enforceClaudeMd"), new GradleBuildSystem().verifyCommand(directory));
	}

	/**
	 * The tool BuildToolchainStep probes must be the one the verification actually
	 * launches, or the check passes for a tool the adoption never runs.
	 */
	@Test
	void mavenRequiresTheToolItsVerificationLaunches(@TempDir Path directory) {
		BuildSystem maven = new MavenBuildSystem();
		assertEquals(Optional.of(maven.verifyCommand(directory).get(0)), maven.requiredTool(directory));
		assertEquals(Optional.of("mvn"), maven.requiredTool(directory));
	}

	@Test
	void gradleRequiresTheToolItsVerificationLaunches(@TempDir Path directory) {
		BuildSystem gradle = new GradleBuildSystem();
		assertEquals(Optional.of(gradle.verifyCommand(directory).get(0)), gradle.requiredTool(directory));
		assertEquals(Optional.of("gradle"), gradle.requiredTool(directory));
	}

	/**
	 * Most Gradle projects ship only the wrapper, so probing the PATH for a `gradle`
	 * aborted the adoption of a repository the host could build perfectly well.
	 */
	@Test
	void gradleVerifiesWithTheCheckoutsWrapperWhenItShipsOne(@TempDir Path directory) throws IOException {
		Path wrapper = Files.writeString(directory.resolve("gradlew"), "#!/bin/sh\n");
		GradleBuildSystem gradle = new GradleBuildSystem(new BuildWrapper("gradlew", "gradlew.bat", false));
		assertEquals(List.of(wrapper.toAbsolutePath().toString(), "-q", "enforceClaudeMd"),
				gradle.verifyCommand(directory));
	}

	@Test
	void mavenVerifiesWithTheCheckoutsWrapperWhenItShipsOne(@TempDir Path directory) throws IOException {
		Path wrapper = Files.writeString(directory.resolve("mvnw"), "#!/bin/sh\n");
		MavenBuildSystem maven = new MavenBuildSystem(new PomEnforcerInstaller("2.6.0"),
				new BuildWrapper("mvnw", "mvnw.cmd", false));
		assertEquals(List.of(wrapper.toAbsolutePath().toString(), "-q", "-N", "validate"),
				maven.verifyCommand(directory));
	}

	/** The wrapper the verification launches is the one the toolchain step probes. */
	@Test
	void theWrapperIsWhatTheToolchainStepProbes(@TempDir Path directory) throws IOException {
		Path wrapper = Files.writeString(directory.resolve("gradlew"), "#!/bin/sh\n");
		GradleBuildSystem gradle = new GradleBuildSystem(new BuildWrapper("gradlew", "gradlew.bat", false));
		assertEquals(Optional.of(wrapper.toAbsolutePath().toString()), gradle.requiredTool(directory));
	}

	/**
	 * Pinning a version changes what Maven wires in, never which build systems are
	 * probed or in which order.
	 */
	@Test
	void theDetectionOrderIsTheSameWhateverTheRuleVersion() {
		assertEquals(BuildSystems.names(BuildSystems.DEFAULTS),
				BuildSystems.names(BuildSystems.defaults(Optional.of("2.6.0"))));
	}

	/**
	 * A snapshot could not be resolved by the adopted project's CI, so it is refused
	 * while the pipeline is still being assembled rather than after a clone and a
	 * {@code claude init}.
	 */
	@Test
	void aSnapshotRuleVersionIsRejectedAsTheBuildSystemsAreBuilt() {
		assertThrows(AdoptionException.class, () -> BuildSystems.defaults(Optional.of("2.6.0-SNAPSHOT")));
	}

	/**
	 * The fallback guard runs through sh, which every supported platform provides
	 * and which has no portable --version probe, so there is nothing to check.
	 */
	@Test
	void theFallbackRequiresNoToolOfItsOwn(@TempDir Path directory) {
		assertEquals(Optional.empty(), new FallbackBuildSystem().requiredTool(directory));
	}
}
