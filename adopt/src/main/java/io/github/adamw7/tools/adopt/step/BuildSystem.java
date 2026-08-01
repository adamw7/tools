package io.github.adamw7.tools.adopt.step;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

/**
 * A build tool the adoption knows how to wire a {@code CLAUDE.md} guard into and
 * run. Abstracting the build tool keeps {@link EnforcerStep} and
 * {@link VerifyStep} agnostic: each detects the checkout's build system once and
 * then installs the guard and runs the verification through this contract, so
 * supporting a new build tool is a matter of adding an implementation rather than
 * branching inside the steps.
 */
public interface BuildSystem {

	/** Human-readable name, e.g. {@code maven} or {@code gradle}, for logging. */
	String name();

	/** @return {@code true} when this build system's build file is present in the checkout. */
	boolean matches(Path repositoryDirectory);

	/**
	 * Wires the {@code CLAUDE.md} guard into the checkout's build file so the
	 * generated file keeps being validated on every build.
	 *
	 * @return {@code true} when the build file was modified, {@code false} when it
	 *         already declared the guard and was left unchanged.
	 */
	boolean install(Path repositoryDirectory);

	/**
	 * Command that runs the wired guard so a missing or malformed {@code CLAUDE.md}
	 * fails the build. The checkout is a parameter because the command depends on
	 * what the checkout itself ships: a project carrying a build wrapper is built
	 * with that wrapper rather than with a build tool off the {@code PATH}.
	 */
	List<String> verifyCommand(Path repositoryDirectory);

	/**
	 * The command that shows the build tool {@link #verifyCommand(Path)} launches can
	 * actually be run, so {@link BuildToolchainStep} can probe it before the pipeline
	 * spends a {@code claude init} on a checkout it will not be able to verify.
	 *
	 * <p>A whole command rather than a program name, because a build system may need
	 * more than one word to launch its tool — a wrapper the checkout committed
	 * without its executable bit goes through {@code sh}, whose own
	 * {@code --version} is not portable — and probing only the program would then
	 * answer for the shell rather than for the build tool.
	 *
	 * @return the probe command, or empty when the verification needs nothing beyond
	 *         the shell
	 */
	default Optional<List<String>> toolProbe(Path repositoryDirectory) {
		return Optional.of(List.of(verifyCommand(repositoryDirectory).get(0), ToolProbe.VERSION_FLAG));
	}
}
