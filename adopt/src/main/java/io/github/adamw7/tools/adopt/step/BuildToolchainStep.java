package io.github.adamw7.tools.adopt.step;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import io.github.adamw7.tools.adopt.AdoptionContext;
import io.github.adamw7.tools.adopt.AdoptionException;
import io.github.adamw7.tools.adopt.command.CommandRunner;

/**
 * Checks that the adopted project's own build tool is installed, as soon as the
 * clone has revealed which one it is. {@link ToolchainStep} can only probe the
 * pipeline's own {@code git}/{@code claude}/{@code gh} because the checkout does
 * not exist yet, but {@link VerifyStep} later shells out to {@code mvn} or
 * {@code gradle} — so without this step a machine without the adopted project's
 * build tool fails at verification, after a full {@code claude init}, a reshaped
 * {@code CLAUDE.md}, and two commits have already been made. That is exactly the
 * late failure the toolchain check exists to prevent.
 *
 * <p>The step runs directly after {@link CloneStep} and detects the build system
 * the same way {@link EnforcerStep} and {@link VerifyStep} do, so the tool it
 * probes is the tool the verification will actually run. A build system that
 * needs no installed tool — the {@link FallbackBuildSystem}, whose guard runs
 * through {@code sh} — and a checkout no configured build system matches are both
 * a no-op, mirroring how those two steps skip rather than fail.
 */
public class BuildToolchainStep implements AdoptionStep {

	private static final Logger log = LogManager.getLogger(BuildToolchainStep.class);

	private final List<BuildSystem> buildSystems;
	private final ToolProbe probe = new ToolProbe();

	public BuildToolchainStep() {
		this(BuildSystems.DEFAULTS);
	}

	public BuildToolchainStep(List<BuildSystem> buildSystems) {
		this.buildSystems = List.copyOf(buildSystems);
	}

	@Override
	public String name() {
		return "build-toolchain";
	}

	@Override
	public void execute(AdoptionContext context, CommandRunner runner) {
		Path repositoryDirectory = context.repositoryDirectory();
		Optional<BuildSystem> buildSystem = BuildSystems.detect(buildSystems, repositoryDirectory);
		buildSystem.ifPresentOrElse(
				detected -> requireTool(detected, repositoryDirectory, runner),
				() -> log.warn("No supported build system ({}) in {}; skipping the build tool check",
						BuildSystems.names(buildSystems), repositoryDirectory));
	}

	private void requireTool(BuildSystem buildSystem, Path repositoryDirectory, CommandRunner runner) {
		buildSystem.requiredTool().ifPresentOrElse(
				tool -> requireInstalled(tool, buildSystem, repositoryDirectory, runner),
				() -> log.info("The {} guard needs no build tool of its own in {}", buildSystem.name(),
						repositoryDirectory));
	}

	private void requireInstalled(String tool, BuildSystem buildSystem, Path repositoryDirectory,
			CommandRunner runner) {
		if (probe.succeeds(List.of(tool, "--version"), repositoryDirectory, runner)) {
			return;
		}
		throw new AdoptionException(name() + " failed: " + repositoryDirectory + " builds with "
				+ buildSystem.name() + " but " + tool + " was not found on the PATH, so the CLAUDE.md guard"
				+ " could not be verified.");
	}
}
