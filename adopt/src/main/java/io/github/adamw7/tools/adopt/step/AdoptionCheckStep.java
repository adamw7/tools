package io.github.adamw7.tools.adopt.step;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import io.github.adamw7.tools.adopt.AdoptionContext;
import io.github.adamw7.tools.adopt.AdoptionException;
import io.github.adamw7.tools.adopt.command.CommandRunner;

/**
 * Reports whether a checkout is actually adopted: it carries a {@code CLAUDE.md},
 * and its build wires in a guard that checks it.
 *
 * <p>It exists because {@link VerifyStep} cannot answer the question: it runs the
 * guard, and a build with no guard wired in passes precisely because nothing ran, so
 * a repository never adopted — or one whose guard a later commit removed — verified
 * exactly like a properly adopted one. That is the drift a fleet-wide check finds.
 *
 * <p>Both halves are reported together rather than at the first missing one, so an
 * operator re-adopting a repository knows whether they are restoring a document, a
 * guard, or both.
 */
public class AdoptionCheckStep extends AbstractBuildSystemStep {

	private static final Logger log = LogManager.getLogger(AdoptionCheckStep.class);

	private static final String CLAUDE_MD = AdoptionAssets.CLAUDE_MD_FILE;

	public AdoptionCheckStep() {
	}

	public AdoptionCheckStep(List<BuildSystem> buildSystems) {
		super(buildSystems);
	}

	@Override
	public String name() {
		return "check-adopted";
	}

	@Override
	protected void onDetected(BuildSystem buildSystem, AdoptionContext context, CommandRunner runner) {
		Path checkout = context.repositoryDirectory();
		List<String> missing = new ArrayList<>();
		collectMissingDocument(checkout, missing);
		collectMissingGuard(buildSystem, checkout, missing);
		if (!missing.isEmpty()) {
			throw new AdoptionException(context.displayUrl() + " is not adopted: " + String.join("; ", missing)
					+ ". Adopt it to restore what is missing.");
		}
		log.info("{} carries {} and a {} guard that checks it", context.displayUrl(), CLAUDE_MD,
				buildSystem.name());
	}

	private void collectMissingDocument(Path checkout, List<String> missing) {
		if (!Files.isRegularFile(checkout.resolve(CLAUDE_MD))) {
			missing.add("no " + CLAUDE_MD + " at its root");
		}
	}

	private void collectMissingGuard(BuildSystem buildSystem, Path checkout, List<String> missing) {
		if (!buildSystem.isGuardInstalled(checkout)) {
			missing.add("its " + buildSystem.name() + " build wires in no CLAUDE.md guard");
		}
	}
}
