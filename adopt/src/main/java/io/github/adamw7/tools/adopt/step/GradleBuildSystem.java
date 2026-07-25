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
 */
public class GradleBuildSystem implements BuildSystem {

	static final List<String> VERIFY_COMMAND = List.of("gradle", "-q", GradleGuardInstaller.GUARD_TASK);
	static final String GROOVY_BUILD_FILE = "build.gradle";
	static final String KOTLIN_BUILD_FILE = "build.gradle.kts";

	private final GradleGuardInstaller installer;

	public GradleBuildSystem() {
		this(new GradleGuardInstaller());
	}

	public GradleBuildSystem(GradleGuardInstaller installer) {
		this.installer = installer;
	}

	@Override
	public String name() {
		return "gradle";
	}

	@Override
	public boolean matches(Path repositoryDirectory) {
		return locate(repositoryDirectory).isPresent();
	}

	@Override
	public boolean install(Path repositoryDirectory) {
		Path buildFile = locate(repositoryDirectory)
				.orElseThrow(() -> new AdoptionException("No Gradle build file in " + repositoryDirectory));
		return installer.install(buildFile);
	}

	@Override
	public List<String> verifyCommand() {
		return VERIFY_COMMAND;
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
