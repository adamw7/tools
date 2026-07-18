package io.github.adamw7.tools.adopt.step;

import java.util.ArrayList;
import java.util.List;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import io.github.adamw7.tools.adopt.AdoptionContext;
import io.github.adamw7.tools.adopt.command.CommandResult;
import io.github.adamw7.tools.adopt.command.CommandRunner;

/**
 * Opens a pull request for the adoption feature branch with the GitHub CLI
 * ({@code gh pr create}), targeting the repository's default branch as the base.
 * The pull request metadata — title, body, reviewers, labels, assignees, and
 * whether it is a draft — is supplied through {@link PullRequestOptions} because
 * it differs between projects; the defaults describe the Claude Code adoption
 * and request nobody.
 *
 * <p>The step stays idempotent when re-run: it asks {@code gh pr view} whether a
 * pull request already exists for the branch and skips creation when one does,
 * rather than creating unconditionally and then matching the wording of a
 * failure. That keeps the decision robust across {@code gh} versions and locales.
 */
public class PullRequestStep extends AbstractCommandStep {

	private static final Logger log = LogManager.getLogger(PullRequestStep.class);

	private final PullRequestOptions options;

	public PullRequestStep() {
		this(PullRequestOptions.defaults());
	}

	public PullRequestStep(String title, String body) {
		this(PullRequestOptions.builder().title(title).body(body).build());
	}

	public PullRequestStep(PullRequestOptions options) {
		this.options = options;
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
		CommandResult result = runOrFail(runner, context.repositoryDirectory(), createCommand(context));
		log.info("Opened pull request: {}", result.output().strip());
	}

	private List<String> createCommand(AdoptionContext context) {
		List<String> command = new ArrayList<>(List.of("gh", "pr", "create", "--title", options.title(), "--body",
				options.body(), "--head", context.branchName()));
		if (options.draft()) {
			command.add("--draft");
		}
		addRepeated(command, "--reviewer", options.reviewers());
		addRepeated(command, "--label", options.labels());
		addRepeated(command, "--assignee", options.assignees());
		return List.copyOf(command);
	}

	private void addRepeated(List<String> command, String flag, List<String> values) {
		for (String value : values) {
			command.add(flag);
			command.add(value);
		}
	}

	private boolean pullRequestExists(AdoptionContext context, CommandRunner runner) {
		List<String> command = List.of("gh", "pr", "view", context.branchName(), "--json", "url");
		return runner.run(context.repositoryDirectory(), command).succeeded();
	}
}
