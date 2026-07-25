package io.github.adamw7.tools.adopt.step;

import java.util.ArrayList;
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
 *
 * <p>The adoption runs headless — on a CI runner or an MCP server — where git may
 * have no configured {@code user.name}/{@code user.email} and {@code git commit}
 * aborts with "Author identity unknown". Each missing identity is supplied for the
 * commit alone with a {@code -c} override, so the commit succeeds on a bare host
 * while an identity the checkout already configures stays in force.
 */
public class CommitStep extends AbstractCommandStep {

	private static final Logger log = LogManager.getLogger(CommitStep.class);

	static final String FALLBACK_NAME = "Claude Code Adopt";
	static final String FALLBACK_EMAIL = "claude-code-adopt@users.noreply.github.com";

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
		runOrFail(runner, context.repositoryDirectory(), commitCommand(context, runner));
		log.info("Committed: {}", message);
	}

	private List<String> commitCommand(AdoptionContext context, CommandRunner runner) {
		List<String> command = new ArrayList<>(List.of("git"));
		addOverrideIfMissing(command, context, runner, "user.name", FALLBACK_NAME);
		addOverrideIfMissing(command, context, runner, "user.email", FALLBACK_EMAIL);
		command.addAll(List.of("commit", "-m", message));
		return command;
	}

	private void addOverrideIfMissing(List<String> command, AdoptionContext context, CommandRunner runner,
			String key, String fallback) {
		if (!hasConfig(context, runner, key)) {
			command.addAll(List.of("-c", key + "=" + fallback));
		}
	}

	private boolean hasConfig(AdoptionContext context, CommandRunner runner, String key) {
		return runner.run(context.repositoryDirectory(), List.of("git", "config", "--get", key)).succeeded();
	}
}
