package io.github.adamw7.tools.adopt.step;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import io.github.adamw7.tools.adopt.AdoptionContext;
import io.github.adamw7.tools.adopt.command.CommandRunner;

/**
 * Wires a {@code CLAUDE.md} guard into the adopted project's build so the
 * generated {@code CLAUDE.md} keeps being validated on every build. The concrete
 * wiring depends on the checkout's build tool: a Maven project gets the full
 * {@code claude-code-enforcer} rule, a Gradle project gets a presence guard task
 * (see {@link BuildSystem}). A repository with no supported build file is skipped
 * with a warning rather than failing the adoption.
 */
public class EnforcerStep implements AdoptionStep {

	private static final Logger log = LogManager.getLogger(EnforcerStep.class);

	private final List<BuildSystem> buildSystems;

	public EnforcerStep() {
		this(BuildSystems.DEFAULTS);
	}

	public EnforcerStep(List<BuildSystem> buildSystems) {
		this.buildSystems = List.copyOf(buildSystems);
	}

	@Override
	public String name() {
		return "enforcer";
	}

	@Override
	public void execute(AdoptionContext context, CommandRunner runner) {
		Path repositoryDirectory = context.repositoryDirectory();
		Optional<BuildSystem> buildSystem = BuildSystems.detect(buildSystems, repositoryDirectory);
		buildSystem.ifPresentOrElse(
				detected -> install(detected, repositoryDirectory),
				() -> log.warn("No supported build system ({}) in {}; skipping enforcer wiring",
						BuildSystems.names(buildSystems), repositoryDirectory));
	}

	private void install(BuildSystem buildSystem, Path repositoryDirectory) {
		if (buildSystem.install(repositoryDirectory)) {
			log.info("Wired the CLAUDE.md guard into the {} build in {}", buildSystem.name(), repositoryDirectory);
		} else {
			log.info("The {} build in {} already declares the guard; left unchanged", buildSystem.name(),
					repositoryDirectory);
		}
	}
}
