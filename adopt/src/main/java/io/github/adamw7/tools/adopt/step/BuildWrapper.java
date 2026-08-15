package io.github.adamw7.tools.adopt.step;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;

import io.github.adamw7.tools.adopt.Platform;
import io.github.adamw7.tools.adopt.command.CommandLine;

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

	/** Launches a wrapper the checkout committed without its executable bit; see {@link #launch}. */
	private static final String SHELL = "sh";

	private final String fileName;
	private final boolean windows;
	private final Predicate<Path> executable;

	/** The wrapper for the host platform: the Windows script there, the POSIX one everywhere else. */
	BuildWrapper(String posixName, String windowsName) {
		this(posixName, windowsName, Platform.isWindows());
	}

	BuildWrapper(String posixName, String windowsName, boolean windows) {
		this(posixName, windowsName, windows, Files::isExecutable);
	}

	/**
	 * The executable bit is read through {@code executable} rather than straight off
	 * the filesystem, because the POSIX one a test needs to describe is not a state a
	 * file can be put into on every host: Windows has no executable bit, so
	 * {@link Files#isExecutable} answers for a plain file there and the shelled branch
	 * below could not be reached on the platform whose wrappers most often need it.
	 * Faking the host with {@code windows} alone left the other half of POSIX real.
	 */
	BuildWrapper(String posixName, String windowsName, boolean windows, Predicate<Path> executable) {
		this.fileName = windows ? windowsName : posixName;
		this.windows = windows;
		this.executable = executable;
	}

	/**
	 * The command to run in the checkout: the wrapper it ships, or {@code fallback}
	 * off the {@code PATH} when it ships none. Assembling it here keeps every build
	 * system's "wrapper, else the PATH tool" from being written out again.
	 */
	List<String> commandIn(Path repositoryDirectory, String fallback, List<String> arguments) {
		return CommandLine.of().addAll(launcherIn(repositoryDirectory, fallback)).addAll(arguments).toList();
	}

	/**
	 * The probe that shows this build tool can actually be launched in the checkout,
	 * built from the same launcher {@link #commandIn} uses so what
	 * {@link BuildToolchainStep} checks is what {@link VerifyStep} will run. Probing
	 * the launcher rather than the wrapper's file name matters for a wrapper that has
	 * to go through {@link #SHELL}, since {@code sh --version} is not portable and a
	 * probe of the program alone would report a working wrapper as unrunnable.
	 */
	List<String> probeIn(Path repositoryDirectory, String fallback) {
		return commandIn(repositoryDirectory, fallback, List.of(ToolProbe.VERSION_FLAG));
	}

	/**
	 * The program-and-arguments prefix that launches the build tool: the checkout's
	 * wrapper, or {@code fallback} off the {@code PATH} when it ships none.
	 */
	private List<String> launcherIn(Path repositoryDirectory, String fallback) {
		return in(repositoryDirectory).map(this::launch).orElse(List.of(fallback));
	}

	/**
	 * A wrapper whose executable bit the project never committed — the usual state of
	 * one added from Windows — cannot be launched as a program at all, so
	 * {@link BuildToolchainStep} aborted the adoption of a repository whose build was
	 * fine. Running it through {@code sh} is what the project's own CI does with it,
	 * and needs no change to the checkout, where a {@code chmod} would leave a mode
	 * change in the adoption's commit that nobody asked for. Only the POSIX wrapper is
	 * ever shelled: a {@code .bat}/{@code .cmd} is not a shell script.
	 */
	private List<String> launch(String wrapper) {
		return windows || executable.test(Path.of(wrapper)) ? List.of(wrapper) : List.of(SHELL, wrapper);
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
