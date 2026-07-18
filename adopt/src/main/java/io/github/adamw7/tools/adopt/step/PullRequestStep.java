package io.github.adamw7.tools.adopt.step;

import java.util.List;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import io.github.adamw7.tools.adopt.AdoptionContext;
import io.github.adamw7.tools.adopt.command.CommandResult;
import io.github.adamw7.tools.adopt.command.CommandRunner;

/**
 * Opens a pull request for the adoption feature branch with the GitHub CLI
 * ({@code gh pr create}), targeting the repository's default branch as the base.
 * The title and body are configurable because they differ between projects; the
 * defaults describe the Claude Code adoption.
 *
 * <p>The step stays idempotent when re-run: it asks {@code gh pr view} whether a
 * pull request already exists for the branch and skips creation when one does,
 * rather than creating unconditionally and then matching the wording of a
 * failure. That keeps the decision robust across {@code gh} versions and locales.
 */
public class PullRequestStep extends AbstractCommandStep {

	private static final Logger log = LogManager.getLogger(PullRequestStep.class);

	static final String DEFAULT_TITLE = "Adopt Claude Code";
	static final String DEFAULT_BODY = "Adds a generated CLAUDE.md and wires the claude-code-enforcer "
			+ "into the build so the file keeps being validated.";

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
		if (pullRequestExists(context, runner)) {
			log.info("Pull request already open for branch {}; left unchanged", context.branchName());
			return;
		}
		log.info("Opening pull request for branch {}", context.branchName());
		List<String> command = List.of("gh", "pr", "create", "--title", title, "--body", body,
				"--head", context.branchName());
		CommandResult result = runOrFail(runner, context.repositoryDirectory(), command);
		log.info("Opened pull request: {}", result.output().strip());
	}

	private boolean pullRequestExists(AdoptionContext context, CommandRunner runner) {
		List<String> command = List.of("gh", "pr", "view", context.branchName(), "--json", "url");
		return runner.run(context.repositoryDirectory(), command).succeeded();
	}
}
