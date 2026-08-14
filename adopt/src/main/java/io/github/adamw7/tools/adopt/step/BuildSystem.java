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
	 * Every checkout-relative path {@link #install(Path)} may write or edit, named by
	 * the build system that writes it so {@link AdoptionAssets} can account for the
	 * adoption's own files without keeping a second copy of each name.
	 *
	 * <p>Deliberately not defaulted: {@link AdoptionAssets#WRITTEN_PATHS} is what
	 * {@link CloneStep} tells the adoption's work apart with and what
	 * {@link CommitStep} refuses an ignored path over, so a file this list did not
	 * name would be silently dropped from the branch. Requiring an answer makes the
	 * compiler ask a new build system for one.
	 *
	 * @return the paths, listed whether or not the checkout happens to carry them
	 */
	List<String> writtenPaths();

	/**
	 * Wires the {@code CLAUDE.md} guard into the checkout's build file so the
	 * generated file keeps being validated on every build.
	 *
	 * @return {@code true} when the build file was modified, {@code false} when it
	 *         already declared the guard and was left unchanged.
	 */
	boolean install(Path repositoryDirectory);

	/**
	 * The {@code CLAUDE.md} sections the guard this build system wires in will hold
	 * the generated file to, so {@link ClaudeMdConformanceStep} reshapes the file
	 * only as far as {@link #install(Path)} and {@link #verifyCommand(Path)} between
	 * them actually demand.
	 *
	 * <p>Empty by default, which is what a presence-and-non-empty guard wants: it
	 * asks for no section in particular, so appending any is noise in the adoption's
	 * first pull request rather than something the build would have rejected. A build
	 * system wiring in a guard that does demand sections names them by overriding.
	 *
	 * @return the required headings, empty when the guard demands none
	 */
	default List<String> requiredClaudeMdSections() {
		return List.of();
	}

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
	 * answer for the shell rather than for the build tool. A verification that names
	 * no command has nothing to probe, and is answered as such rather than with the
	 * {@link IndexOutOfBoundsException} reading its first word would raise.
	 *
	 * @return the probe command, or empty when the verification needs nothing beyond
	 *         the shell
	 */
	default Optional<List<String>> toolProbe(Path repositoryDirectory) {
		List<String> verify = verifyCommand(repositoryDirectory);
		if (verify.isEmpty()) {
			return Optional.empty();
		}
		return Optional.of(List.of(verify.get(0), ToolProbe.VERSION_FLAG));
	}

	/**
	 * What an operator whose {@link #toolProbe(Path)} could not be run has to do
	 * about it, named by the build system rather than by {@link BuildToolchainStep}:
	 * the remedy is not the same for every one of them, and a step that spelled out
	 * one of them told a project with no build file at all to "install github-actions
	 * on the PATH", which names nothing installable.
	 *
	 * @return the remedy, as a sentence
	 */
	default String toolAdvice() {
		return "Install " + name() + " on the PATH, or commit the project's build wrapper.";
	}
}
