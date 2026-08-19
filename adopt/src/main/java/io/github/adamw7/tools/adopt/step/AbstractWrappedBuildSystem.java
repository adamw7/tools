package io.github.adamw7.tools.adopt.step;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

/**
 * Base for the build systems launched through a tool the checkout may ship a
 * wrapper for — {@code mvnw} for Maven, {@code gradlew} for Gradle. Both verify
 * the guard by running that tool with a fixed set of arguments, and both have to
 * probe whatever {@link #verifyCommand(Path)} will launch rather than the program
 * name, so a wrapper that has to go through a shell is probed as it will be run.
 * Saying that once keeps the probe and the verification from drifting apart in one
 * build system but not the other.
 */
abstract class AbstractWrappedBuildSystem implements BuildSystem {

	private final String name;
	private final String tool;
	private final List<String> verifyArguments;
	private final BuildWrapper wrapper;

	/**
	 * The name and the program are taken separately because they are not always the
	 * same word: Maven builds run with {@code mvn} but the build system is called
	 * {@code maven}. Declaring the program as both let the base report a name Maven
	 * then had to override — so the one build system whose two differ was also the one
	 * whose name this class did not decide.
	 *
	 * @param name            what this build system is called in a message
	 * @param tool            the program to launch when the checkout ships no wrapper
	 * @param verifyArguments the arguments that run the wired-in guard
	 * @param wrapper         the wrapper script to prefer over {@code tool}
	 */
	protected AbstractWrappedBuildSystem(String name, String tool, List<String> verifyArguments,
			BuildWrapper wrapper) {
		this.name = name;
		this.tool = tool;
		this.verifyArguments = List.copyOf(verifyArguments);
		this.wrapper = wrapper;
	}

	@Override
	public final String name() {
		return name;
	}

	@Override
	public List<String> verifyCommand(Path repositoryDirectory) {
		return wrapper.commandIn(repositoryDirectory, tool, verifyArguments);
	}

	/** Probes whatever {@link #verifyCommand(Path)} will launch — the wrapper, however it has to be run. */
	@Override
	public Optional<List<String>> toolProbe(Path repositoryDirectory) {
		return Optional.of(wrapper.probeIn(repositoryDirectory, tool));
	}
}
