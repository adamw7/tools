package io.github.adamw7.tools.adopt.step;

import java.util.List;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import io.github.adamw7.tools.adopt.AdoptionContext;
import io.github.adamw7.tools.adopt.AdoptionException;
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
 * <p>Staging is also where the adoption finds out that the checkout excludes a file
 * it wrote, {@code git add -A} skipping an ignored path silently. That is refused
 * rather than committed around — see
 * {@link #requireNothingTheAdoptionWroteIsIgnored}.
 *
 * <p>The pipeline runs this step two or three times, so each is qualified with what
 * it commits: a report whose {@code completedSteps} read {@code commit} three times
 * said only how far the run got by counting.
 */
public class CommitStep extends AbstractCommandStep {

	private static final Logger log = LogManager.getLogger(CommitStep.class);

	static final String FALLBACK_NAME = "Claude Code Adopt";
	static final String FALLBACK_EMAIL = "claude-code-adopt@users.noreply.github.com";

	/** The step name an unqualified commit reports, and the prefix a qualified one carries. */
	static final String NAME = "commit";

	/** The exit code {@code git diff --quiet} reports differences with; see {@link #hasStagedChanges}. */
	private static final int STAGED_CHANGES = 1;

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
		requireNothingTheAdoptionWroteIsIgnored(context, runner);
		runOrFail(runner, context.repositoryDirectory(), List.of("git", "add", "-A"));
		if (hasStagedChanges(context, runner)) {
			commit(context, runner);
		} else {
			log.info("No changes to commit for: {}", message);
		}
	}

	/**
	 * Refuses to commit while a file the adoption wrote is excluded by the checkout's
	 * ignore rules. {@code git add -A} skips an ignored path in silence, so the file
	 * stays in the working tree — where {@link VerifyStep} goes on finding it and
	 * passing — while the branch that is pushed carries nothing of it.
	 *
	 * <p>Nothing downstream could catch that. A repository ignoring {@code CLAUDE.md}
	 * was adopted, reported as complete, and left with the guard the adoption had just
	 * wired in failing on a clean checkout of its own pull request — the one outcome
	 * {@link VerifyStep} exists to prevent. Failing is the answer rather than forcing
	 * the files in: the pattern is the project's own decision about its repository.
	 */
	private void requireNothingTheAdoptionWroteIsIgnored(AdoptionContext context, CommandRunner runner) {
		List<String> ignored = ignoredPaths(context, runner);
		if (!ignored.isEmpty()) {
			throw new AdoptionException(name() + " cannot commit " + String.join(", ", ignored) + " in "
					+ context.repositoryDirectory() + ": the checkout's ignore rules exclude those paths, so they"
					+ " would stay out of " + context.branchName() + " while the verification kept reading them from"
					+ " the working tree. Stop ignoring those paths, or adopt a repository that does not.");
		}
	}

	/**
	 * The paths git would leave behind: the ones the adoption writes that exist in the
	 * checkout, are not tracked, and are excluded. A path the checkout already tracks
	 * is committed whatever the ignore rules say, so {@code --others} is what asks the
	 * question.
	 *
	 * <p>Only {@link AdoptionAssets#WRITTEN_PATHS} is asked about, and only those are
	 * read back out: the transcript merges the command's standard error, so a git that
	 * warns puts a line in it that is no path of ours.
	 */
	private List<String> ignoredPaths(AdoptionContext context, CommandRunner runner) {
		CommandResult result = runOrFail(runner, context.repositoryDirectory(),
				CommandLine.of("git", "ls-files", "--others", "--ignored", "--exclude-standard", "--")
						.addAll(AdoptionAssets.WRITTEN_PATHS)
						.toList());
		return result.output().lines()
				.map(String::strip)
				.filter(AdoptionAssets.WRITTEN_PATHS::contains)
				.distinct()
				.toList();
	}

	/**
	 * {@code git diff --quiet} answers with its exit code: zero for nothing staged, and
	 * {@value #STAGED_CHANGES} for something. Anything above that is the command
	 * failing rather than answering, and reading every non-zero code as "there is
	 * something to commit" turned it into a {@code git commit} failure two lines
	 * later, naming the commit rather than the query that could not be run.
	 */
	private boolean hasStagedChanges(AdoptionContext context, CommandRunner runner) {
		CommandResult result = runner.run(context.repositoryDirectory(),
				List.of("git", "diff", "--cached", "--quiet"));
		if (result.exitCode() > STAGED_CHANGES) {
			throw new AdoptionException(name() + " could not ask git what is staged in "
					+ context.repositoryDirectory() + ": " + result.redactedOutput().strip());
		}
		return result.exitCode() == STAGED_CHANGES;
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
