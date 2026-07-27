package io.github.adamw7.tools.adopt.step;

import java.nio.file.Path;
import java.util.List;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import io.github.adamw7.tools.adopt.AdoptionContext;
import io.github.adamw7.tools.adopt.AdoptionException;
import io.github.adamw7.tools.adopt.command.CommandRunner;

/**
 * Checks that the adopted project's own build tool is installed, as soon as the
 * clone has revealed which one it is. {@link ToolchainStep} can only probe the
 * pipeline's own {@code git}/{@code claude}/{@code gh}, but {@link VerifyStep}
 * later shells out to {@code mvn} or {@code gradle} — so without this step a
 * machine missing that build tool fails at verification, after a full
 * {@code claude init} and two commits.
 *
 * <p>The build system is detected through {@link AbstractBuildSystemStep} just as
 * {@link EnforcerStep} and {@link VerifyStep} detect it, so the tool probed is the
 * tool the verification runs. A build system that needs none — the
 * {@link FallbackBuildSystem}, whose guard runs through {@code sh} — is a no-op.
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
		buildSystem.requiredTool(repositoryDirectory).ifPresentOrElse(
				tool -> requireRunnable(tool, buildSystem, repositoryDirectory, runner),
				() -> log.info("The {} guard needs no build tool of its own in {}", buildSystem.name(),
						repositoryDirectory));
	}

	/**
	 * The tool probed is whatever the verification will launch, which for a checkout
	 * shipping a build wrapper is that wrapper rather than a program on the
	 * {@code PATH} — so the failure says the tool could not be run rather than that
	 * it was not installed, which for a wrapper present but unusable would be wrong.
	 */
	private void requireRunnable(String tool, BuildSystem buildSystem, Path repositoryDirectory,
			CommandRunner runner) {
		if (probe.isInstalled(tool, repositoryDirectory, runner)) {
			return;
		}
		throw new AdoptionException(name() + " failed: " + repositoryDirectory + " builds with "
				+ buildSystem.name() + " but " + tool + " could not be run, so the CLAUDE.md guard"
				+ " could not be verified. Install " + buildSystem.name() + " on the PATH, or commit the"
				+ " project's build wrapper.");
	}
}
