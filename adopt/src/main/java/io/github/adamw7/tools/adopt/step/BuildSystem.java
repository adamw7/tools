package io.github.adamw7.tools.adopt.step;

import java.nio.file.Path;
import java.util.List;

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

	/** Command that runs the wired guard so a missing or malformed {@code CLAUDE.md} fails the build. */
	List<String> verifyCommand();
}
