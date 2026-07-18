package io.github.adamw7.tools.adopt.step;

import java.util.List;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import io.github.adamw7.tools.adopt.AdoptionContext;
import io.github.adamw7.tools.adopt.command.CommandResult;
import io.github.adamw7.tools.adopt.command.CommandRunner;

/**
 * Stages every change in the checkout and commits it with the configured
 * message. Whether there is anything to commit is decided by asking git
 * ({@code git diff --cached --quiet}) rather than by matching the wording of a
 * failed commit's output, so an empty commit is a harmless no-op regardless of
 * git's locale or version and the pipeline stays idempotent when re-run.
 */
public class CommitStep extends AbstractCommandStep {

	private static final Logger log = LogManager.getLogger(CommitStep.class);

	private final String message;

	public CommitStep(String message) {
		this.message = message;
	}

	@Override
	public String name() {
		return "commit";
	}

	@Override
	public void execute(AdoptionContext context, CommandRunner runner) {
		runOrFail(runner, context.repositoryDirectory(), List.of("git", "add", "-A"));
		if (hasStagedChanges(context, runner)) {
			commit(context, runner);
		} else {
			log.info("No changes to commit for: {}", message);
		}
	}

	private boolean hasStagedChanges(AdoptionContext context, CommandRunner runner) {
		CommandResult result = runner.run(context.repositoryDirectory(),
				List.of("git", "diff", "--cached", "--quiet"));
		return !result.succeeded();
	}

	private void commit(AdoptionContext context, CommandRunner runner) {
		runOrFail(runner, context.repositoryDirectory(), List.of("git", "commit", "-m", message));
		log.info("Committed: {}", message);
	}
}
