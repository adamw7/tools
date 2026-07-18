package io.github.adamw7.tools.adopt.step;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import io.github.adamw7.tools.adopt.AdoptionContext;
import io.github.adamw7.tools.adopt.command.CommandRunner;

/**
 * Clones the target repository into the workspace with {@code git clone}, so the
 * remaining steps have a working checkout to operate on. The step is idempotent:
 * a checkout that already exists (its {@code .git} directory is present) is
 * reused rather than re-cloned, so re-running the adoption against the same
 * workspace does not abort on an "already exists" clone failure.
 */
public class CloneStep extends AbstractCommandStep {

	private static final Logger log = LogManager.getLogger(CloneStep.class);

	@Override
	public String name() {
		return "clone";
	}

	@Override
	public void execute(AdoptionContext context, CommandRunner runner) {
		if (alreadyCloned(context)) {
			log.info("{} already contains a checkout; skipping clone", context.repositoryDirectory());
			return;
		}
		log.info("Cloning {} into {}", context.repositoryUrl(), context.repositoryDirectory());
		List<String> command = List.of("git", "clone", context.repositoryUrl(),
				context.repositoryDirectory().toString());
		runOrFail(runner, context.workspace(), command);
	}

	private boolean alreadyCloned(AdoptionContext context) {
		Path gitDirectory = context.repositoryDirectory().resolve(".git");
		return Files.isDirectory(gitDirectory);
	}
}
