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

	/** Command that runs the wired guard so a missing or malformed {@code CLAUDE.md} fails the build. */
	List<String> verifyCommand();

	/**
	 * The same build system, pinning an explicitly supplied
	 * {@code claude-code-enforcer} version into whatever it wires in. Only a build
	 * system that wires in a versioned artifact of its own has anything to pin — the
	 * Gradle and fallback guards are self-contained — so the default is to answer
	 * with this build system unchanged, and a new implementation opts in by
	 * overriding rather than by being named in a check elsewhere.
	 *
	 * @param ruleVersion a released rule version
	 */
	default BuildSystem withRuleVersion(String ruleVersion) {
		return this;
	}

	/**
	 * The program {@link #verifyCommand()} launches, so {@link BuildToolchainStep}
	 * can probe it before the pipeline spends a {@code claude init} on a checkout it
	 * will not be able to verify. The verification launches the first word of its
	 * own command, so that is what the default probes.
	 *
	 * @return the program to probe, or empty when the verification needs nothing
	 *         beyond the POSIX shell every supported platform already provides
	 */
	default Optional<String> requiredTool() {
		return Optional.of(verifyCommand().get(0));
	}
}
