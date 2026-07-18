package io.github.adamw7.tools.adopt.step;

import java.util.List;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import io.github.adamw7.tools.adopt.AdoptionContext;
import io.github.adamw7.tools.adopt.command.CommandRunner;

/**
 * Creates and checks out the adoption feature branch in the fresh checkout with
 * {@code git checkout -B}, so every subsequent commit lands on that branch
 * rather than on the repository's default branch. The adoption pushes this
 * branch and opens a pull request from it, leaving the default branch untouched.
 *
 * <p>{@code -B} resets the branch to the current {@code HEAD} whether or not it
 * already exists, so re-running the adoption against a checkout that already
 * carries the branch starts the feature branch afresh rather than aborting on an
 * "already exists" failure.
 */
public class BranchStep extends AbstractCommandStep {

	private static final Logger log = LogManager.getLogger(BranchStep.class);

	@Override
	public String name() {
		return "branch";
	}

	@Override
	public void execute(AdoptionContext context, CommandRunner runner) {
		log.info("Creating branch {} in {}", context.branchName(), context.repositoryDirectory());
		List<String> command = List.of("git", "checkout", "-B", context.branchName());
		runOrFail(runner, context.repositoryDirectory(), command);
	}
}
