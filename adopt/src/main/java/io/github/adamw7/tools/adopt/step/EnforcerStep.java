package io.github.adamw7.tools.adopt.step;

import java.nio.file.Path;
import java.util.List;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import io.github.adamw7.tools.adopt.AdoptionContext;
import io.github.adamw7.tools.adopt.command.CommandRunner;

/**
 * Wires a {@code CLAUDE.md} guard into the adopted project's build so the generated
 * file keeps being validated. The wiring depends on the checkout's build tool: Maven
 * gets the full {@code claude-code-enforcer} rule, Gradle a presence guard task, and
 * a project with no recognised build file the build-tool-agnostic GitHub Actions
 * guard (see {@link BuildSystem}). {@link AbstractBuildSystemStep} detects it, and
 * skips the step with a warning when none matches rather than failing the adoption.
 */
public class EnforcerStep extends AbstractBuildSystemStep {

	private static final Logger log = LogManager.getLogger(EnforcerStep.class);

	public EnforcerStep() {
	}

	public EnforcerStep(List<BuildSystem> buildSystems) {
		super(buildSystems);
	}

	@Override
	public String name() {
		return "enforcer";
	}

	@Override
	protected void onDetected(BuildSystem buildSystem, AdoptionContext context, CommandRunner runner) {
		Path repositoryDirectory = context.repositoryDirectory();
		if (buildSystem.install(repositoryDirectory)) {
			log.info("Wired the CLAUDE.md guard into the {} build in {}", buildSystem.name(), repositoryDirectory);
		} else {
			log.info("The {} build in {} already declares the guard; left unchanged", buildSystem.name(),
					repositoryDirectory);
		}
	}
}
