package io.github.adamw7.tools.adopt.step;

import java.util.List;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import io.github.adamw7.tools.adopt.AdoptionContext;
import io.github.adamw7.tools.adopt.command.CommandRunner;

/**
 * Pushes the adoption feature branch to the repository's origin with
 * {@code git push -u origin <branch>}, setting the upstream so the freshly
 * created branch is published and can be the head of a pull request.
 */
public class PushStep extends AbstractCommandStep {

	private static final Logger log = LogManager.getLogger(PushStep.class);

	@Override
	public String name() {
		return "push";
	}

	@Override
	public void execute(AdoptionContext context, CommandRunner runner) {
		log.info("Pushing branch {} from {}", context.branchName(), context.repositoryDirectory());
		List<String> command = List.of("git", "push", "-u", "origin", context.branchName());
		runOrFail(runner, context.repositoryDirectory(), command);
	}
}
