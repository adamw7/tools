package io.github.adamw7.tools.adopt.step;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import io.github.adamw7.tools.adopt.Platform;

/**
 * Finds the build wrapper script a checkout ships with — {@code mvnw} for Maven,
 * {@code gradlew} for Gradle — so the adoption verifies the {@code CLAUDE.md}
 * guard with the build tool the project itself expects to be built with.
 *
 * <p>Most Gradle projects ship only the wrapper and no system-wide {@code gradle},
 * so probing the {@code PATH} for one failed {@link BuildToolchainStep} and
 * aborted the adoption of a repository the host could have built perfectly well.
 * Preferring the wrapper also pins the build to the version the project pinned,
 * which is the whole reason a wrapper is committed.
 *
 * <p>The wrapper is answered as an <em>absolute</em> path, because
 * {@link ProcessBuilder} resolves a relative program name against the JVM's own
 * working directory rather than the one the command is given.
 */
final class BuildWrapper {

	private final String fileName;

	/** The wrapper for the host platform: the Windows script there, the POSIX one everywhere else. */
	BuildWrapper(String posixName, String windowsName) {
		this(posixName, windowsName, Platform.isWindows());
	}

	BuildWrapper(String posixName, String windowsName, boolean windows) {
		this.fileName = windows ? windowsName : posixName;
	}

	/**
	 * The command to run in the checkout: the wrapper it ships, or {@code fallback}
	 * off the {@code PATH} when it ships none. Assembling it here keeps every build
	 * system's "wrapper, else the PATH tool" from being written out again.
	 */
	List<String> commandIn(Path repositoryDirectory, String fallback, List<String> arguments) {
		List<String> command = new ArrayList<>();
		command.add(in(repositoryDirectory).orElse(fallback));
		command.addAll(arguments);
		return List.copyOf(command);
	}

	/**
	 * @return the absolute path of the checkout's wrapper, or empty when it ships
	 *         none and the build tool has to come from the {@code PATH}
	 */
	Optional<String> in(Path repositoryDirectory) {
		Path wrapper = repositoryDirectory.resolve(fileName);
		return Files.isRegularFile(wrapper) ? Optional.of(wrapper.toAbsolutePath().toString()) : Optional.empty();
	}
}
