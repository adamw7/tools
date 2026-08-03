package io.github.adamw7.tools.adopt.step;

import java.util.List;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import io.github.adamw7.tools.adopt.AdoptionContext;
import io.github.adamw7.tools.adopt.Text;
import io.github.adamw7.tools.adopt.command.CommandLine;
import io.github.adamw7.tools.adopt.command.CommandResult;
import io.github.adamw7.tools.adopt.command.CommandRunner;

/**
 * Stages every change in the checkout and commits it with the configured message.
 * Whether there is anything to commit is decided by asking git
 * ({@code git diff --cached --quiet}) rather than by matching the wording of a
 * failed commit's output, so an empty commit is a harmless no-op regardless of
 * git's locale or version.
 *
 * <p>The adoption runs headless — on a CI runner or an MCP server — where git may
 * have no configured {@code user.name}/{@code user.email} and {@code git commit}
 * aborts with "Author identity unknown". Each missing identity is supplied for the
 * commit alone with a {@code -c} override, so an identity the checkout already
 * configures stays in force.
 *
 * <p>The pipeline runs this step two or three times, so each one is qualified with
 * what it commits. A report whose {@code completedSteps} read {@code commit} three
 * times said only how far the run got by counting; qualified, it says which of the
 * adoption's commits landed — the reading that matters for a run that stopped
 * part-way, which is the only kind of report anyone reads closely.
 */
public class CommitStep extends AbstractCommandStep {

	private static final Logger log = LogManager.getLogger(CommitStep.class);

	static final String FALLBACK_NAME = "Claude Code Adopt";
	static final String FALLBACK_EMAIL = "claude-code-adopt@users.noreply.github.com";

	/** The step name an unqualified commit reports, and the prefix a qualified one carries. */
	static final String NAME = "commit";

	private final String message;
	private final String name;

	/** A commit that reports itself as the bare {@value #NAME}. */
	public CommitStep(String message) {
		this(message, null);
	}

	/**
	 * @param message   the commit message
	 * @param qualifier what this commit is for, reported as {@code commit:<qualifier>};
	 *                  blank or {@code null} for a commit that names only itself
	 */
	public CommitStep(String message, String qualifier) {
		this.message = message;
		this.name = Text.isPresent(qualifier) ? NAME + ":" + qualifier.strip() : NAME;
	}

	@Override
	public String name() {
		return name;
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
		CommandLine command = CommandLine.of("git");
		addOverrideIfMissing(command, context, runner, "user.name", FALLBACK_NAME);
		addOverrideIfMissing(command, context, runner, "user.email", FALLBACK_EMAIL);
		return command.add("commit", "-m", message).toList();
	}

	private void addOverrideIfMissing(CommandLine command, AdoptionContext context, CommandRunner runner,
			String key, String fallback) {
		command.addIf(!hasConfig(context, runner, key), "-c", key + "=" + fallback);
	}

	private boolean hasConfig(AdoptionContext context, CommandRunner runner, String key) {
		return runner.run(context.repositoryDirectory(), List.of("git", "config", "--get", key)).succeeded();
	}
}
