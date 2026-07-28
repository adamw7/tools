package io.github.adamw7.tools.adopt.step;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import io.github.adamw7.tools.adopt.AdoptionContext;
import io.github.adamw7.tools.adopt.AdoptionException;
import io.github.adamw7.tools.adopt.Redaction;
import io.github.adamw7.tools.adopt.command.CommandResult;
import io.github.adamw7.tools.adopt.command.CommandRunner;

/**
 * Clones the target repository into the workspace with {@code git clone}, so the
 * remaining steps have a working checkout to operate on. The step is idempotent:
 * a checkout that already exists (its {@code .git} directory is present) is
 * reused rather than re-cloned, so re-running the adoption against the same
 * workspace does not abort on an "already exists" clone failure.
 *
 * <p>A reused checkout is confirmed to be the repository under adoption before
 * anything is done to it, by comparing the repository its {@code origin} names
 * with the one the run was given. A checkout directory is named after the
 * repository alone, so two repositories of the same name — {@code alice/tools}
 * and {@code bob/tools}, or one repository and its fork — claim one directory
 * under a named {@code --workspace}. {@link io.github.adamw7.tools.adopt.Checkouts}
 * catches that collision within a single run, but two runs against the same
 * workspace never meet; without this check the second adoption would silently
 * branch, commit, and push the first repository's working tree, and report the
 * first repository's pull request as the second's.
 *
 * <p>The checkout is then refreshed with {@code git fetch}, because every later
 * decision about the feature branch is taken against the remote-tracking refs a
 * stale checkout has out of date.
 */
public class CloneStep extends AbstractCommandStep {

	private static final Logger log = LogManager.getLogger(CloneStep.class);

	/** The remote a clone records, and the one {@link PushStep} publishes the branch to. */
	private static final String REMOTE = "origin";

	@Override
	public String name() {
		return "clone";
	}

	@Override
	public void execute(AdoptionContext context, CommandRunner runner) {
		if (alreadyCloned(context)) {
			reuse(context, runner);
		} else {
			cloneAfresh(context, runner);
		}
	}

	private void cloneAfresh(AdoptionContext context, CommandRunner runner) {
		log.info("Cloning {} into {}", context.displayUrl(), context.repositoryDirectory());
		List<String> command = List.of("git", "clone", context.repositoryUrl(),
				context.repositoryDirectory().toString());
		runOrFail(runner, context.workspace(), command);
	}

	private void reuse(AdoptionContext context, CommandRunner runner) {
		requireCheckoutOfTheSameRepository(context, runner);
		log.info("{} already contains a checkout of {}; skipping clone", context.repositoryDirectory(),
				context.displayUrl());
		refresh(context, runner);
	}

	/**
	 * The failure names both repositories and the directory they disagree about,
	 * since that is what the operator has to act on — and it says what to do, because
	 * neither answer is obvious: adopt into a workspace of its own, or clear the
	 * directory the other repository left behind.
	 */
	private void requireCheckoutOfTheSameRepository(AdoptionContext context, CommandRunner runner) {
		String origin = originUrl(context, runner);
		if (!context.isSameRepository(origin)) {
			throw new AdoptionException(context.repositoryDirectory() + " already holds a checkout of "
					+ Redaction.of(origin) + ", not of " + context.displayUrl()
					+ ". Adopt this repository into its own --workspace, or remove that directory first.");
		}
	}

	/**
	 * A directory carrying a {@code .git} but no {@code origin} is reported rather
	 * than adopted: it cannot be shown to be the repository under adoption, and
	 * {@link PushStep} would have nowhere to publish the branch to anyway.
	 */
	private String originUrl(AdoptionContext context, CommandRunner runner) {
		CommandResult result = runner.run(context.repositoryDirectory(),
				List.of("git", "remote", "get-url", REMOTE));
		if (!result.succeeded()) {
			throw new AdoptionException(context.repositoryDirectory() + " is a checkout with no '" + REMOTE
					+ "' remote, so it can be neither confirmed to be " + context.displayUrl() + " nor pushed to it: "
					+ Redaction.of(result.output().strip()));
		}
		return result.output().strip();
	}

	/**
	 * Brings the reused checkout's remote-tracking refs up to date, so
	 * {@link BranchStep} sees a feature branch an earlier adoption published and
	 * resumes from its tip. A checkout that predates that push carries no
	 * {@code origin/<branch>} ref, which would restart the branch at the default
	 * branch and leave {@link PushStep} refused as a non-fast-forward.
	 */
	private void refresh(AdoptionContext context, CommandRunner runner) {
		log.info("Fetching {} in {}", REMOTE, context.repositoryDirectory());
		runOrFail(runner, context.repositoryDirectory(), List.of("git", "fetch", REMOTE));
	}

	private boolean alreadyCloned(AdoptionContext context) {
		Path gitDirectory = context.repositoryDirectory().resolve(".git");
		return Files.isDirectory(gitDirectory);
	}
}
