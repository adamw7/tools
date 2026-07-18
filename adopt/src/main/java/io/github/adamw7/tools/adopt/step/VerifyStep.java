package io.github.adamw7.tools.adopt.step;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import io.github.adamw7.tools.adopt.AdoptionContext;
import io.github.adamw7.tools.adopt.command.CommandRunner;

/**
 * Runs the guard wired in by {@link EnforcerStep} so a missing or malformed
 * {@code CLAUDE.md} fails the adoption locally instead of breaking the
 * contributor's build after the pull request lands. The command to run is
 * chosen from the checkout's build tool — a non-recursive {@code mvn -N validate}
 * for Maven, the guard task for Gradle (see {@link BuildSystem}). A repository
 * with no supported build file has nothing to verify and is skipped, mirroring
 * {@link EnforcerStep}.
 */
public class VerifyStep extends AbstractCommandStep {

	private static final Logger log = LogManager.getLogger(VerifyStep.class);

	private final List<BuildSystem> buildSystems;

	public VerifyStep() {
		this(BuildSystems.DEFAULTS);
	}

	public VerifyStep(List<BuildSystem> buildSystems) {
		this.buildSystems = List.copyOf(buildSystems);
	}

	@Override
	public String name() {
		return "verify";
	}

	@Override
	public void execute(AdoptionContext context, CommandRunner runner) {
		Path repositoryDirectory = context.repositoryDirectory();
		Optional<BuildSystem> buildSystem = BuildSystems.detect(buildSystems, repositoryDirectory);
		buildSystem.ifPresentOrElse(
				detected -> verify(detected, context, runner),
				() -> log.warn("No supported build system ({}) in {}; skipping build verification",
						BuildSystems.names(buildSystems), repositoryDirectory));
	}

	private void verify(BuildSystem buildSystem, AdoptionContext context, CommandRunner runner) {
		log.info("Verifying the CLAUDE.md guard passes with {} in {}", buildSystem.name(),
				context.repositoryDirectory());
		runOrFail(runner, context.repositoryDirectory(), buildSystem.verifyCommand());
	}
}
