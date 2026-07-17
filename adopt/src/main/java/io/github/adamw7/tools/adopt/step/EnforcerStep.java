package io.github.adamw7.tools.adopt.step;

import java.nio.file.Files;
import java.nio.file.Path;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import io.github.adamw7.tools.adopt.AdoptionContext;
import io.github.adamw7.tools.adopt.command.CommandRunner;

/**
 * Wires the {@code claude-code-enforcer} into the adopted project's build so
 * the generated {@code CLAUDE.md} keeps being validated on every build. The
 * step edits the checkout's root {@code pom.xml}; a repository that is not a
 * Maven project (no {@code pom.xml}) is skipped with a warning rather than
 * failing the adoption.
 */
public class EnforcerStep implements AdoptionStep {

	private static final Logger log = LogManager.getLogger(EnforcerStep.class);

	private static final String POM = "pom.xml";

	private final PomEnforcerInstaller installer;

	public EnforcerStep() {
		this(new PomEnforcerInstaller());
	}

	public EnforcerStep(PomEnforcerInstaller installer) {
		this.installer = installer;
	}

	@Override
	public String name() {
		return "enforcer";
	}

	@Override
	public void execute(AdoptionContext context, CommandRunner runner) {
		Path pomFile = context.repositoryDirectory().resolve(POM);
		if (Files.isRegularFile(pomFile)) {
			install(pomFile);
		} else {
			log.warn("No {} in {}; skipping enforcer wiring", POM, context.repositoryDirectory());
		}
	}

	private void install(Path pomFile) {
		if (installer.install(pomFile)) {
			log.info("Added claude-code-enforcer to {}", pomFile);
		} else {
			log.info("{} already declares the enforcer plugin; left unchanged", pomFile);
		}
	}
}
