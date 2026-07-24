package io.github.adamw7.tools.adopt.step;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

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
 *
 * <p>A checkout that carries no local branch yet but whose {@code origin} already
 * publishes one — a fresh clone re-adopting a repository an earlier run already
 * pushed, which is what the default temporary workspace produces on every run —
 * starts the branch from that published tip instead of from {@code HEAD}. Without
 * it the branch would restart at the default branch, and {@link PushStep} would
 * be rejected as a non-fast-forward, so a second adoption could never get past
 * the push even though every other step is idempotent. A local branch that
 * already exists is left to the plain {@code -B} above, so unpushed work in a
 * reused workspace is never reset onto the remote.
 */
public class BranchStep extends AbstractCommandStep {

	private static final Logger log = LogManager.getLogger(BranchStep.class);

	private static final String REMOTE = "origin";

	@Override
	public String name() {
		return "branch";
	}

	@Override
	public void execute(AdoptionContext context, CommandRunner runner) {
		log.info("Creating branch {} in {}", context.branchName(), context.repositoryDirectory());
		runOrFail(runner, context.repositoryDirectory(), checkoutCommand(context, runner));
	}

	private List<String> checkoutCommand(AdoptionContext context, CommandRunner runner) {
		List<String> command = new ArrayList<>(List.of("git", "checkout", "-B", context.branchName()));
		startPoint(context, runner).ifPresent(command::add);
		return List.copyOf(command);
	}

	/**
	 * @return the published branch to start from, or empty when the checkout
	 *         already carries the branch locally or {@code origin} does not publish
	 *         it yet — in both cases the branch starts from {@code HEAD} as before.
	 */
	private Optional<String> startPoint(AdoptionContext context, CommandRunner runner) {
		if (hasLocalBranch(context, runner) || !hasRemoteBranch(context, runner)) {
			return Optional.empty();
		}
		log.info("Resuming branch {} from {}", context.branchName(), remoteBranch(context));
		return Optional.of(remoteBranch(context));
	}

	private boolean hasLocalBranch(AdoptionContext context, CommandRunner runner) {
		return hasRef(context, runner, "refs/heads/" + context.branchName());
	}

	private boolean hasRemoteBranch(AdoptionContext context, CommandRunner runner) {
		return hasRef(context, runner, "refs/remotes/" + remoteBranch(context));
	}

	private String remoteBranch(AdoptionContext context) {
		return REMOTE + "/" + context.branchName();
	}

	private boolean hasRef(AdoptionContext context, CommandRunner runner, String ref) {
		List<String> command = List.of("git", "rev-parse", "--verify", "--quiet", ref);
		return runner.run(context.repositoryDirectory(), command).succeeded();
	}
}
