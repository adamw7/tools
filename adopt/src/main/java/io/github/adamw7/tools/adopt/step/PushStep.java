package io.github.adamw7.tools.adopt.step;

import java.util.List;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import io.github.adamw7.tools.adopt.AdoptionContext;
import io.github.adamw7.tools.adopt.command.CommandRunner;

/**
 * Pushes the committed adoption changes back to the repository's origin with
 * {@code git push}.
 */
public class PushStep extends AbstractCommandStep {

	private static final Logger log = LogManager.getLogger(PushStep.class);

	@Override
	public String name() {
		return "push";
	}

	@Override
	public void execute(AdoptionContext context, CommandRunner runner) {
		log.info("Pushing changes from {}", context.repositoryDirectory());
		runOrFail(runner, context.repositoryDirectory(), List.of("git", "push"));
	}
}
