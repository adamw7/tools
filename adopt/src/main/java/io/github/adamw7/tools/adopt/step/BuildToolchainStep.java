package io.github.adamw7.tools.adopt.step;

import java.nio.file.Path;
import java.util.List;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import io.github.adamw7.tools.adopt.AdoptionContext;
import io.github.adamw7.tools.adopt.AdoptionException;
import io.github.adamw7.tools.adopt.command.CommandResult;
import io.github.adamw7.tools.adopt.command.CommandRunner;

/**
 * Checks that the adopted project's own build tool is installed, as soon as the
 * clone has revealed which one it is. {@link ToolchainStep} can only probe the
 * pipeline's own {@code git}/{@code claude}/{@code gh}, but {@link VerifyStep}
 * later shells out to {@code mvn} or {@code gradle} — so without this step a
 * machine missing that build tool fails at verification, after a full
 * {@code claude init} and two commits.
 *
 * <p>The build system is detected through {@link AbstractBuildSystemStep} as
 * {@link EnforcerStep} and {@link VerifyStep} detect it, so the tool probed is the
 * one the verification runs — including the {@link FallbackBuildSystem}'s shell,
 * absent from a stock Windows {@code PATH}. A build system naming no probe is a no-op.
 */
public class BuildToolchainStep extends AbstractBuildSystemStep {

	private static final Logger log = LogManager.getLogger(BuildToolchainStep.class);

	private final ToolProbe probe = new ToolProbe();

	public BuildToolchainStep() {
	}

	public BuildToolchainStep(List<BuildSystem> buildSystems) {
		super(buildSystems);
	}

	@Override
	public String name() {
		return "build-toolchain";
	}

	@Override
	protected void onDetected(BuildSystem buildSystem, AdoptionContext context, CommandRunner runner) {
		Path repositoryDirectory = context.repositoryDirectory();
		buildSystem.toolProbe(repositoryDirectory).ifPresentOrElse(
				command -> requireRunnable(command, buildSystem, repositoryDirectory, runner),
				() -> log.info("The {} guard needs no build tool of its own in {}", buildSystem.name(),
						repositoryDirectory));
	}

	/**
	 * The tool probed is whatever the verification will launch, which for a checkout
	 * shipping a wrapper is that wrapper — so the failure says the tool could not be
	 * run rather than that it was not installed, which for a wrapper present but
	 * unusable would be wrong. The whole probe command is named, that being how the
	 * operator reproduces it.
	 */
	private void requireRunnable(List<String> command, BuildSystem buildSystem, Path repositoryDirectory,
			CommandRunner runner) {
		if (probe.succeeds(command, repositoryDirectory, runner)) {
			return;
		}
		throw new AdoptionException(name() + " failed: " + repositoryDirectory + " builds with "
				+ buildSystem.name() + " but " + CommandResult.describe(command) + " could not be run, so the"
				+ " CLAUDE.md guard could not be verified. " + buildSystem.toolAdvice());
	}
}
