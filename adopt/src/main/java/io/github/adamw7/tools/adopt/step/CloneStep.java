package io.github.adamw7.tools.adopt.step;

import java.util.List;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import io.github.adamw7.tools.adopt.AdoptionContext;
import io.github.adamw7.tools.adopt.command.CommandRunner;

/**
 * Clones the target repository into the workspace with {@code git clone}, so the
 * remaining steps have a working checkout to operate on.
 */
public class CloneStep extends AbstractCommandStep {

	private static final Logger log = LogManager.getLogger(CloneStep.class);

	@Override
	public String name() {
		return "clone";
	}

	@Override
	public void execute(AdoptionContext context, CommandRunner runner) {
		log.info("Cloning {} into {}", context.repositoryUrl(), context.repositoryDirectory());
		List<String> command = List.of("git", "clone", context.repositoryUrl(),
				context.repositoryDirectory().toString());
		runOrFail(runner, context.workspace(), command);
	}
}
