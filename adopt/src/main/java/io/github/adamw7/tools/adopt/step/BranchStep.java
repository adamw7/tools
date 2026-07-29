package io.github.adamw7.tools.adopt.step;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import io.github.adamw7.tools.adopt.AdoptionContext;
import io.github.adamw7.tools.adopt.command.CommandRunner;

/**
 * Creates and checks out the adoption feature branch with
 * {@code git checkout -B}, so every subsequent commit lands there rather than on
 * the repository's default branch. {@code -B} resets the branch to the current
 * {@code HEAD} whether or not it already exists, so re-running the adoption starts
 * it afresh rather than aborting.
 *
 * <p>A checkout with no local branch yet but whose {@code origin} already
 * publishes one — a fresh clone re-adopting a repository an earlier run pushed,
 * which the default temporary workspace produces every time — starts from that
 * published tip instead. Otherwise the branch would restart at the default branch
 * and {@link PushStep} would be rejected as a non-fast-forward. An existing local
 * branch is left to the plain {@code -B}, so unpushed work is never reset onto the
 * remote.
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
	 *         it yet — in both cases the branch starts from {@code HEAD}
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
		return AdoptionContext.REMOTE + "/" + context.branchName();
	}

	private boolean hasRef(AdoptionContext context, CommandRunner runner, String ref) {
		List<String> command = List.of("git", "rev-parse", "--verify", "--quiet", ref);
		return runner.run(context.repositoryDirectory(), command).succeeded();
	}
}
