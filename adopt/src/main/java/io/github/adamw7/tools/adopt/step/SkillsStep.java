package io.github.adamw7.tools.adopt.step;

import java.nio.file.Path;
import java.util.List;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import io.github.adamw7.tools.adopt.AdoptionContext;
import io.github.adamw7.tools.adopt.command.CommandRunner;

/**
 * Writes the starter Claude Code skills (see {@link StarterSkills}) into the
 * checkout, so an adopted repository carries the conventions the adoption
 * established rather than only the document describing them. It runs beside
 * {@link AssetsStep} under the same {@code --assets} switch and before the same
 * commit, the skills being configuration assets like the rest.
 *
 * <p>Separate from {@link AssetsStep} because a skill's body depends on the
 * checkout's build system — which guard was wired in, and what runs it — and
 * {@link AbstractBuildSystemStep} is where that is detected once for every step
 * that needs it. Folding the skills into the static asset list would instead have
 * made the whole list conditional on a build system being detected at all.
 *
 * <p>Nothing is overwritten: {@link AssetInstaller} leaves an existing file alone and
 * {@link StarterSkills} passes over a name the project's own definitions already
 * claim, so the step is idempotent and safe to re-run.
 */
public class SkillsStep extends AbstractBuildSystemStep {

	private static final Logger log = LogManager.getLogger(SkillsStep.class);

	public SkillsStep() {
	}

	public SkillsStep(List<BuildSystem> buildSystems) {
		super(buildSystems);
	}

	@Override
	public String name() {
		return "skills";
	}

	@Override
	protected void onDetected(BuildSystem buildSystem, AdoptionContext context, CommandRunner runner) {
		Path repositoryDirectory = context.repositoryDirectory();
		long installed = StarterSkills.forCheckout(buildSystem, repositoryDirectory).stream()
				.filter(installer -> installer.install(repositoryDirectory))
				.count();
		log.info("Installed {} starter skill(s) describing the {} build under {}/{}", installed,
				buildSystem.name(), repositoryDirectory, StarterSkills.SKILLS_DIRECTORY);
	}
}
