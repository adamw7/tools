package io.github.adamw7.tools.adopt.step;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import io.github.adamw7.tools.adopt.AdoptionException;

/**
 * Gradle support for the adoption: detects a Groovy ({@code build.gradle}) or
 * Kotlin ({@code build.gradle.kts}) build script, appends a {@code CLAUDE.md}
 * guard task with {@link GradleGuardInstaller}, and verifies it by running that
 * task. Gradle has no {@code claude-code-enforcer} equivalent, so the guard is a
 * presence-and-non-empty check rather than the full Maven format rule.
 *
 * <p>A checkout that ships a {@code gradlew} is verified with that wrapper rather
 * than with a {@code gradle} off the {@code PATH}. Most Gradle projects ship only
 * the wrapper, so this is the ordinary case rather than the exception: without it
 * {@link BuildToolchainStep} found no {@code gradle} to probe and aborted the
 * adoption. See {@link AbstractWrappedBuildSystem}.
 */
public class GradleBuildSystem extends AbstractWrappedBuildSystem {

	static final String GRADLE = "gradle";
	private static final List<String> VERIFY_ARGUMENTS = List.of("-q", GradleGuardInstaller.GUARD_TASK);
	static final String GROOVY_BUILD_FILE = "build.gradle";
	static final String KOTLIN_BUILD_FILE = "build.gradle.kts";

	private final GradleGuardInstaller installer = new GradleGuardInstaller();

	public GradleBuildSystem() {
		this(new BuildWrapper("gradlew", "gradlew.bat"));
	}

	GradleBuildSystem(BuildWrapper wrapper) {
		super(GRADLE, VERIFY_ARGUMENTS, wrapper);
	}

	@Override
	public boolean matches(Path repositoryDirectory) {
		return locate(repositoryDirectory).isPresent();
	}

	/**
	 * Both build scripts, not merely the one {@link #locate} would pick: the checkout
	 * carrying either is a file the adoption may edit, and which of them it is is not
	 * known until a repository is in hand.
	 */
	@Override
	public List<String> writtenPaths() {
		return List.of(GROOVY_BUILD_FILE, KOTLIN_BUILD_FILE);
	}

	@Override
	public boolean install(Path repositoryDirectory) {
		Path buildFile = locate(repositoryDirectory)
				.orElseThrow(() -> new AdoptionException("No Gradle build file in " + repositoryDirectory));
		return installer.install(buildFile);
	}

	/**
	 * Prefers the Groovy build file over the Kotlin one when a checkout carries
	 * both, matching the order most Gradle projects resolve them in.
	 */
	private Optional<Path> locate(Path repositoryDirectory) {
		return candidate(repositoryDirectory, GROOVY_BUILD_FILE)
				.or(() -> candidate(repositoryDirectory, KOTLIN_BUILD_FILE));
	}

	private Optional<Path> candidate(Path repositoryDirectory, String fileName) {
		Path candidate = repositoryDirectory.resolve(fileName);
		return Files.isRegularFile(candidate) ? Optional.of(candidate) : Optional.empty();
	}
}
