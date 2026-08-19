package io.github.adamw7.tools.adopt.step;

import java.util.List;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import io.github.adamw7.tools.adopt.AdoptionContext;
import io.github.adamw7.tools.adopt.command.CommandRunner;

/**
 * Runs the guard wired in by {@link EnforcerStep} so a missing or malformed
 * {@code CLAUDE.md} fails the adoption locally instead of breaking the contributor's
 * build after the pull request lands. The command follows the checkout's build tool
 * — {@code mvn -N validate}, the Gradle guard task, or the guard script (see
 * {@link BuildSystem}). {@link AbstractBuildSystemStep} detects it and skips the step
 * with a warning when none matches.
 */
public class VerifyStep extends AbstractBuildSystemStep {

	private static final Logger log = LogManager.getLogger(VerifyStep.class);

	public VerifyStep() {
	}

	public VerifyStep(List<BuildSystem> buildSystems) {
		super(buildSystems);
	}

	@Override
	public String name() {
		return "verify";
	}

	@Override
	protected void onDetected(BuildSystem buildSystem, AdoptionContext context, CommandRunner runner) {
		log.info("Verifying the CLAUDE.md guard passes with {} in {}", buildSystem.name(),
				context.repositoryDirectory());
		runOrFail(runner, context.repositoryDirectory(), buildSystem.verifyCommand(context.repositoryDirectory()));
	}
}
