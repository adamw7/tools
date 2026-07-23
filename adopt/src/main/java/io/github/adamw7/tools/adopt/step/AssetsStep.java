package io.github.adamw7.tools.adopt.step;

import java.nio.file.Path;
import java.util.List;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import io.github.adamw7.tools.adopt.AdoptionContext;
import io.github.adamw7.tools.adopt.command.CommandRunner;

/**
 * Installs the starter Claude Code configuration assets (see
 * {@link AdoptionAssets}) into the checkout, so an adopted repository gets a
 * working agent setup beyond the generated {@code CLAUDE.md}. Each asset is
 * installed independently and never overwrites an existing file, so the step is
 * idempotent and safe on repositories that already configured some of the
 * files. The step only writes into the checkout; the following commit step
 * records whatever was added.
 */
public class AssetsStep implements AdoptionStep {

	private static final Logger log = LogManager.getLogger(AssetsStep.class);

	private final List<AssetInstaller> installers;

	public AssetsStep() {
		this(AdoptionAssets.DEFAULTS);
	}

	public AssetsStep(List<AssetInstaller> installers) {
		this.installers = List.copyOf(installers);
	}

	@Override
	public String name() {
		return "assets";
	}

	@Override
	public void execute(AdoptionContext context, CommandRunner runner) {
		for (AssetInstaller installer : installers) {
			install(installer, context.repositoryDirectory());
		}
	}

	private void install(AssetInstaller installer, Path repositoryDirectory) {
		if (installer.install(repositoryDirectory)) {
			log.info("Installed {}", installer.relativePath());
		} else {
			log.info("{} already exists; left unchanged", installer.relativePath());
		}
	}
}
