package io.github.adamw7.tools.adopt.step;

import java.util.List;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import io.github.adamw7.tools.adopt.AdoptionContext;
import io.github.adamw7.tools.adopt.AdoptionException;
import io.github.adamw7.tools.adopt.command.CommandResult;
import io.github.adamw7.tools.adopt.command.CommandRunner;

/**
 * Stages every change in the checkout and commits it with the configured
 * message. A commit that finds nothing to record is treated as a harmless
 * no-op rather than a failure, so the pipeline stays idempotent when re-run.
 */
public class CommitStep extends AbstractCommandStep {

	private static final Logger log = LogManager.getLogger(CommitStep.class);

	private static final String NOTHING_TO_COMMIT = "nothing to commit";

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
		commit(context, runner);
	}

	private void commit(AdoptionContext context, CommandRunner runner) {
		List<String> command = List.of("git", "commit", "-m", message);
		CommandResult result = runner.run(context.repositoryDirectory(), command);
		reportCommit(result);
	}

	private void reportCommit(CommandResult result) {
		if (result.succeeded()) {
			log.info("Committed: {}", message);
		} else if (result.output().contains(NOTHING_TO_COMMIT)) {
			log.info("No changes to commit for: {}", message);
		} else {
			throw new AdoptionException(name() + " failed (exit " + result.exitCode() + ") running: "
					+ result.describe() + System.lineSeparator() + result.output());
		}
	}
}
