package io.github.adamw7.tools.adopt.step;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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

	/**
	 * {@link AdoptionAssets#WRITTEN_PATHS} is built once from {@link BuildSystems#DEFAULTS},
	 * but the pipeline runs on {@link BuildSystems#defaults(Optional)} with whatever rule
	 * version the run pinned. The two lists are read for different things and nothing
	 * makes them agree, so a build system whose written paths depended on its
	 * configuration would drop out of the guards that list feeds: {@link CloneStep} would
	 * read the file it writes as a contributor's uncommitted work and refuse to resume,
	 * and {@link CommitStep} would stop noticing that the checkout ignores it — the
	 * adoption reported complete with the guard it wired in missing from the branch.
	 * Pinning the invariant here fails this module's build the day one does, rather than
	 * some adopted repository's.
	 */
	@Test
	void everyPinnedRuleVersionYieldsTheWrittenPathsTheGuardsWereBuiltFrom() {
		List<String> fromDefaults = writtenPathsOf(BuildSystems.DEFAULTS);

		assertEquals(fromDefaults, writtenPathsOf(BuildSystems.defaults(Optional.empty())));
		assertEquals(fromDefaults, writtenPathsOf(BuildSystems.defaults(Optional.of("2.5.0"))));
		assertTrue(AdoptionAssets.WRITTEN_PATHS.containsAll(fromDefaults),
				"every build file a guard is wired into must be a path the adoption accounts for");
	}

	private List<String> writtenPathsOf(List<BuildSystem> buildSystems) {
		return buildSystems.stream().map(BuildSystem::writtenPaths).flatMap(List::stream).toList();
	}

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
		assertEquals(Optional.of(List.of("mvn", "--version")), maven.toolProbe(directory));
	}

	@Test
	void gradleRequiresTheToolItsVerificationLaunches(@TempDir Path directory) {
		BuildSystem gradle = new GradleBuildSystem();
		assertEquals(Optional.of(List.of("gradle", "--version")), gradle.toolProbe(directory));
	}

	/**
	 * Most Gradle projects ship only the wrapper, so probing the PATH for a `gradle`
	 * aborted the adoption of a repository the host could build perfectly well.
	 */
	@Test
	void gradleVerifiesWithTheCheckoutsWrapperWhenItShipsOne(@TempDir Path directory) throws IOException {
		Path wrapper = executable(directory.resolve("gradlew"));
		GradleBuildSystem gradle = new GradleBuildSystem(new BuildWrapper("gradlew", "gradlew.bat", false));
		assertEquals(List.of(wrapper.toAbsolutePath().toString(), "-q", "enforceClaudeMd"),
				gradle.verifyCommand(directory));
	}

	@Test
	void mavenVerifiesWithTheCheckoutsWrapperWhenItShipsOne(@TempDir Path directory) throws IOException {
		Path wrapper = executable(directory.resolve("mvnw"));
		MavenBuildSystem maven = new MavenBuildSystem(new PomEnforcerInstaller("2.6.0"),
				new BuildWrapper("mvnw", "mvnw.cmd", false));
		assertEquals(List.of(wrapper.toAbsolutePath().toString(), "-q", "-N", "validate"),
				maven.verifyCommand(directory));
	}

	/**
	 * A wrapper committed without its executable bit — the usual state of one added
	 * from Windows — cannot be launched as a program, so it is run through sh. It was
	 * otherwise refused with "Permission denied" and aborted the adoption of a
	 * repository whose build was fine.
	 */
	@Test
	void aWrapperWithoutItsExecutableBitIsLaunchedThroughTheShell(@TempDir Path directory) throws IOException {
		Path wrapper = Files.writeString(directory.resolve("gradlew"), "#!/bin/sh\n");
		GradleBuildSystem gradle = new GradleBuildSystem(new BuildWrapper("gradlew", "gradlew.bat", false));
		assertEquals(List.of("sh", wrapper.toAbsolutePath().toString(), "-q", "enforceClaudeMd"),
				gradle.verifyCommand(directory));
	}

	/**
	 * The probe of a shelled wrapper must be the whole launcher: `sh --version` is
	 * not portable — under dash it exits non-zero — so probing the program alone
	 * answered for the shell rather than for the build tool.
	 */
	@Test
	void aShelledWrapperIsProbedThroughTheShellToo(@TempDir Path directory) throws IOException {
		Path wrapper = Files.writeString(directory.resolve("mvnw"), "#!/bin/sh\n");
		MavenBuildSystem maven = new MavenBuildSystem(new PomEnforcerInstaller("2.6.0"),
				new BuildWrapper("mvnw", "mvnw.cmd", false));
		assertEquals(Optional.of(List.of("sh", wrapper.toAbsolutePath().toString(), "--version")),
				maven.toolProbe(directory));
	}

	/** The wrapper the verification launches is the one the toolchain step probes. */
	@Test
	void theWrapperIsWhatTheToolchainStepProbes(@TempDir Path directory) throws IOException {
		Path wrapper = executable(directory.resolve("gradlew"));
		GradleBuildSystem gradle = new GradleBuildSystem(new BuildWrapper("gradlew", "gradlew.bat", false));
		assertEquals(Optional.of(List.of(wrapper.toAbsolutePath().toString(), "--version")),
				gradle.toolProbe(directory));
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
	 * The fallback guard runs through sh, so sh is what it probes. Answering "nothing
	 * to check" assumed every host has a shell on its PATH — a stock Windows one has
	 * not — and left that to be discovered at the verification, three steps and a
	 * claude init later.
	 */
	@Test
	void theFallbackProbesTheShellItsGuardRunsThrough(@TempDir Path directory) {
		assertEquals(Optional.of(List.of("sh", "-c", "exit 0")),
				new FallbackBuildSystem().toolProbe(directory));
	}

	/**
	 * The probe asks the shell to do nothing rather than for its version, because
	 * {@code sh --version} is not portable: dash exits non-zero, which would report a
	 * perfectly good shell as unusable.
	 */
	@Test
	void theFallbackProbeAsksNothingOfTheShellThatDashWouldRefuse(@TempDir Path directory) {
		assertFalse(new FallbackBuildSystem().toolProbe(directory).orElseThrow().contains("--version"));
	}

	/** The shell probed and the shell the verification launches must be the one shell. */
	@Test
	void theFallbackProbesTheSameShellItVerifiesWith(@TempDir Path directory) {
		FallbackBuildSystem fallback = new FallbackBuildSystem();
		assertEquals(fallback.verifyCommand(directory).get(0), fallback.toolProbe(directory).orElseThrow().get(0));
	}

	/**
	 * "Install github-actions on the PATH" names nothing installable, which is what
	 * the step said before each build system answered for its own remedy.
	 */
	@Test
	void theFallbackAdvisesAboutAShellRatherThanAboutItself() {
		String advice = new FallbackBuildSystem().toolAdvice();
		assertTrue(advice.contains("sh"), advice);
		assertFalse(advice.contains("Install github-actions"), advice);
	}

	@Test
	void aBuildToolIsAdvisedAboutByName() {
		assertTrue(new MavenBuildSystem().toolAdvice().contains("maven"), "maven should name itself");
		assertTrue(new GradleBuildSystem().toolAdvice().contains("gradle"), "gradle should name itself");
	}

	/**
	 * {@link BuildSystem#toolProbe(Path)} is the default a new build system inherits,
	 * and it reads the first word of the verify command. One that names no command has
	 * nothing to probe, and must be told so rather than be handed an
	 * {@link IndexOutOfBoundsException} several steps into an adoption.
	 */
	@Test
	void aBuildSystemWithNoVerifyCommandHasNothingToProbe(@TempDir Path directory) {
		assertEquals(Optional.empty(), new NoVerifyCommandBuildSystem().toolProbe(directory));
	}

	/** A build system that verifies nothing, standing in for one a contributor adds. */
	private static final class NoVerifyCommandBuildSystem implements BuildSystem {

		@Override
		public String name() {
			return "none";
		}

		@Override
		public boolean matches(Path repositoryDirectory) {
			return false;
		}

		@Override
		public List<String> writtenPaths() {
			return List.of();
		}

		@Override
		public boolean install(Path repositoryDirectory) {
			return false;
		}

		@Override
		public List<String> verifyCommand(Path repositoryDirectory) {
			return List.of();
		}
	}

	private Path executable(Path wrapper) throws IOException {
		Files.writeString(wrapper, "#!/bin/sh\n");
		assertTrue(wrapper.toFile().setExecutable(true), "could not make " + wrapper + " executable");
		return wrapper;
	}
}
