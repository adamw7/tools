package io.github.adamw7.tools.adopt.step;

import java.nio.file.Path;
import java.util.List;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import io.github.adamw7.tools.adopt.AdoptionContext;
import io.github.adamw7.tools.adopt.command.CommandRunner;

/**
 * Base for the steps that act on the checkout's build system: wiring the
 * {@code CLAUDE.md} guard into it ({@link EnforcerStep}), running that guard
 * ({@link VerifyStep}), and checking the build tool it needs is installed
 * ({@link BuildToolchainStep}). Detecting the build system in one place keeps
 * all three acting on the same build tool, so the guard that is wired in is the
 * guard that is verified with the tool that was probed.
 *
 * <p>Detection only comes up empty for a step configured with a build-system list
 * that has no catch-all fallback ({@link BuildSystems#DEFAULTS} always matches),
 * in which case the step is skipped with a warning rather than failing the run.
 */
public abstract class AbstractBuildSystemStep extends AbstractCommandStep {

	private static final Logger log = LogManager.getLogger(AbstractBuildSystemStep.class);

	private final List<BuildSystem> buildSystems;

	/** Detects the checkout's build system among {@link BuildSystems#DEFAULTS}. */
	protected AbstractBuildSystemStep() {
		this(BuildSystems.DEFAULTS);
	}

	protected AbstractBuildSystemStep(List<BuildSystem> buildSystems) {
		this.buildSystems = List.copyOf(buildSystems);
	}

	@Override
	public void execute(AdoptionContext context, CommandRunner runner) {
		Path repositoryDirectory = context.repositoryDirectory();
		BuildSystems.detect(buildSystems, repositoryDirectory).ifPresentOrElse(
				detected -> onDetected(detected, context, runner),
				() -> log.warn("No supported build system ({}) in {}; skipping the {} step",
						BuildSystems.names(buildSystems), repositoryDirectory, name()));
	}

	/** What the step does with the build system detected in the checkout. */
	protected abstract void onDetected(BuildSystem buildSystem, AdoptionContext context, CommandRunner runner);
}
