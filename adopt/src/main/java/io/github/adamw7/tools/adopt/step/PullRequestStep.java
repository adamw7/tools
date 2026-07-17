package io.github.adamw7.tools.adopt.step;

import java.util.List;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import io.github.adamw7.tools.adopt.AdoptionContext;
import io.github.adamw7.tools.adopt.AdoptionException;
import io.github.adamw7.tools.adopt.command.CommandResult;
import io.github.adamw7.tools.adopt.command.CommandRunner;

/**
 * Opens a pull request for the adoption feature branch with the GitHub CLI
 * ({@code gh pr create}), targeting the repository's default branch as the base.
 * The title and body are configurable because they differ between projects; the
 * defaults describe the Claude Code adoption.
 *
 * <p>Like {@link CommitStep}, the step stays idempotent when re-run: a
 * {@code gh} failure that only reports an already-open pull request for the
 * branch, or that there are no commits between base and head, is treated as a
 * harmless no-op rather than aborting the adoption.
 */
public class PullRequestStep extends AbstractCommandStep {

	private static final Logger log = LogManager.getLogger(PullRequestStep.class);

	static final String DEFAULT_TITLE = "Adopt Claude Code";
	static final String DEFAULT_BODY = "Adds a generated CLAUDE.md and wires the claude-code-enforcer "
			+ "into the build so the file keeps being validated.";

	private static final String ALREADY_EXISTS = "already exists";
	private static final String NO_COMMITS = "No commits between";

	private final String title;
	private final String body;

	public PullRequestStep() {
		this(DEFAULT_TITLE, DEFAULT_BODY);
	}

	public PullRequestStep(String title, String body) {
		this.title = title;
		this.body = body;
	}

	@Override
	public String name() {
		return "pull-request";
	}

	@Override
	public void execute(AdoptionContext context, CommandRunner runner) {
		log.info("Opening pull request for branch {}", context.branchName());
		List<String> command = List.of("gh", "pr", "create", "--title", title, "--body", body,
				"--head", context.branchName());
		reportPullRequest(runner.run(context.repositoryDirectory(), command));
	}

	private void reportPullRequest(CommandResult result) {
		if (result.succeeded()) {
			log.info("Opened pull request: {}", result.output().strip());
		} else if (alreadyHandled(result)) {
			log.info("No pull request opened: {}", result.output().strip());
		} else {
			throw new AdoptionException(name() + " failed (exit " + result.exitCode() + ") running: "
					+ result.describe() + System.lineSeparator() + result.output());
		}
	}

	private boolean alreadyHandled(CommandResult result) {
		return result.output().contains(ALREADY_EXISTS) || result.output().contains(NO_COMMITS);
	}
}
