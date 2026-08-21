package io.github.adamw7.tools.adopt.step;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.util.List;
import java.util.Optional;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import io.github.adamw7.tools.adopt.AdoptionContext;
import io.github.adamw7.tools.adopt.AdoptionException;
import io.github.adamw7.tools.adopt.Failures;
import io.github.adamw7.tools.adopt.command.CommandResult;
import io.github.adamw7.tools.adopt.command.CommandRunner;
import io.github.adamw7.tools.adopt.command.RetryingCommandRunner.Pause;

/**
 * Runs the Claude Code CLI in headless mode against the checkout so it generates a
 * {@code CLAUDE.md} for the project. The invocation is configurable because the
 * flags differ between environments; the default runs {@code /init}
 * non-interactively with {@code --permission-mode acceptEdits}, since headless
 * {@code -p} mode has no interactive approver and would otherwise only print a
 * request to write the file, exit cleanly, and leave nothing behind.
 *
 * <p>A repository that already carries a {@code .claude/CLAUDE.md} steers headless
 * {@code /init} into updating <em>that</em> file rather than the root one the
 * adoption needs — and a write under {@code .claude/} is refused as a sensitive
 * path — so that memory file is moved aside before the run and restored
 * afterwards, on the failure path too. A run that exits cleanly but leaves no
 * {@code CLAUDE.md} behind aborts the adoption rather than pushing an empty pull
 * request.
 *
 * <p>The step is idempotent: a checkout that already carries a root
 * {@code CLAUDE.md} is left alone, because the CLI's output is not reproducible
 * and regenerating would discard whatever has been edited since.
 *
 * <p>A run that leaves no {@code CLAUDE.md} is tried again, a bounded number of
 * times. This is the run's most expensive command and the only one with no other
 * recovery, {@link io.github.adamw7.tools.adopt.command.TransientFailures} leaving
 * {@code claude} out because its transcript is a model's prose. What is judged here
 * is the <em>outcome</em> — whether the file exists — which that objection does not
 * reach: a run that produced the file succeeded whatever it exited with, and one
 * that did not produced nothing to lose by trying again.
 */
public class ClaudeInitStep extends AbstractCommandStep {

	private static final Logger log = LogManager.getLogger(ClaudeInitStep.class);

	static final List<String> DEFAULT_COMMAND = List.of("claude", "-p", "/init",
			"--permission-mode", "acceptEdits");

	private static final String CLAUDE_MD = AdoptionAssets.CLAUDE_MD_FILE;
	private static final String CLAUDE_DIR = ".claude";

	/**
	 * The wait before a second attempt, doubling before each one after it. Longer than
	 * the transport backoff next door because what is being waited out is different:
	 * a model that declined, timed out, or answered without writing anything, none of
	 * which a two-second pause changes.
	 */
	static final Duration FIRST_BACKOFF = Duration.ofSeconds(5);

	/** The wait the doubling stops at, so a generous retry count cannot idle a run for minutes. */
	static final Duration MAX_BACKOFF = Duration.ofSeconds(30);

	private final List<String> claudeCommand;
	private final int retries;
	private final Pause pause;

	public ClaudeInitStep() {
		this(DEFAULT_COMMAND);
	}

	/**
	 * The default invocation, retried the number of times the run allows.
	 *
	 * @param retries how many further attempts a run that produced no {@code CLAUDE.md}
	 *                earns
	 */
	public static ClaudeInitStep retrying(int retries) {
		return new ClaudeInitStep(DEFAULT_COMMAND, retries);
	}

	public ClaudeInitStep(List<String> claudeCommand) {
		this(claudeCommand, 0);
	}

	/**
	 * @param retries how many further attempts a run that produced no {@code CLAUDE.md}
	 *                earns; zero runs the CLI exactly once, as an undecorated step does
	 */
	public ClaudeInitStep(List<String> claudeCommand, int retries) {
		this(claudeCommand, retries, Pause.sleeping());
	}

	ClaudeInitStep(List<String> claudeCommand, int retries, Pause pause) {
		this.claudeCommand = List.copyOf(claudeCommand);
		this.retries = retries;
		this.pause = pause;
	}

	@Override
	public String name() {
		return "claude-init";
	}

	@Override
	public void execute(AdoptionContext context, CommandRunner runner) {
		Path checkout = context.repositoryDirectory();
		if (Files.isRegularFile(checkout.resolve(CLAUDE_MD))) {
			log.info("{} already carries {}; left unchanged", checkout, CLAUDE_MD);
			return;
		}
		CommandResult result = attemptUntilGenerated(context, runner, checkout);
		requireGenerated(context, result);
	}

	/**
	 * Runs the CLI until it leaves a {@code CLAUDE.md} behind or the attempts run out,
	 * answering the last attempt's result so the failure names what the CLI actually
	 * said. The memory file is moved aside and restored around <em>each</em> attempt,
	 * because a second attempt has to meet the same checkout the first one did.
	 */
	private CommandResult attemptUntilGenerated(AdoptionContext context, CommandRunner runner, Path checkout) {
		CommandResult result = attempt(checkout, runner, 1);
		for (int attempt = 1; attempt <= retries && !generated(context); attempt++) {
			waitBefore(attempt, result);
			result = attempt(checkout, runner, attempt + 1);
		}
		return result;
	}

	private CommandResult attempt(Path checkout, CommandRunner runner, int attempt) {
		log.info("Running claude init in {} (attempt {} of {})", checkout, attempt, retries + 1);
		Optional<Path> relocated = relocateExistingClaudeDirMemory(checkout);
		CommandResult result;
		try {
			result = runOrFail(runner, checkout, claudeCommand);
		} catch (RuntimeException e) {
			Failures.alsoRun(e, () -> restoreRelocated(relocated, checkout));
			throw e;
		}
		restoreRelocated(relocated, checkout);
		return result;
	}

	private boolean generated(AdoptionContext context) {
		return Files.isRegularFile(context.repositoryDirectory().resolve(CLAUDE_MD));
	}

	/**
	 * The attempt being replaced is logged here because nothing else will: an attempt a
	 * later one succeeds after would otherwise leave no trace, and the transcript is
	 * where a reader finds out that the CLI declined rather than failed.
	 */
	private void waitBefore(int attempt, CommandResult result) {
		Duration backoff = backoff(attempt);
		log.warn("claude init produced no {} (exit {}); retrying in {}s ({} of {}): {}", CLAUDE_MD,
				result.exitCode(), backoff.toSeconds(), attempt, retries, result.redactedOutput().strip());
		pause.of(backoff);
	}

	private Duration backoff(int attempt) {
		Duration doubled = FIRST_BACKOFF.multipliedBy(1L << (attempt - 1));
		return doubled.compareTo(MAX_BACKOFF) > 0 ? MAX_BACKOFF : doubled;
	}



	private void restoreRelocated(Optional<Path> relocated, Path checkout) {
		relocated.ifPresent(backup -> restore(backup, claudeDirMemory(checkout)));
	}

	private Path claudeDirMemory(Path checkout) {
		return checkout.resolve(CLAUDE_DIR).resolve(CLAUDE_MD);
	}

	/**
	 * Moves an existing {@code .claude/CLAUDE.md} out of the checkout, into the
	 * system temp directory so a later {@code git add -A} cannot pick it up.
	 *
	 * @return the backup location, or empty when the checkout carried no such file
	 */
	private Optional<Path> relocateExistingClaudeDirMemory(Path checkout) {
		Path memory = claudeDirMemory(checkout);
		if (!Files.isRegularFile(memory)) {
			return Optional.empty();
		}
		return Optional.of(moveAside(memory));
	}

	private Path moveAside(Path memory) {
		try {
			Path backup = Files.createTempFile("adopt-claude-dir-memory-", ".bak");
			Files.move(memory, backup, StandardCopyOption.REPLACE_EXISTING);
			log.info("Moved existing {} aside so /init writes the root {}", memory, CLAUDE_MD);
			return backup;
		} catch (IOException e) {
			throw new AdoptionException(name() + " could not move aside existing " + memory, e);
		}
	}

	/**
	 * The path is absolutised before its parent is taken, so a memory file named relative
	 * to the working directory — which {@link Path#getParent} answers {@code null} for —
	 * still names the directory the move will land in instead of being dereferenced.
	 */
	private void restore(Path backup, Path memory) {
		try {
			Path directory = memory.toAbsolutePath().getParent();
			if (directory != null) {
				Files.createDirectories(directory);
			}
			Files.move(backup, memory, StandardCopyOption.REPLACE_EXISTING);
			log.info("Restored existing {}", memory);
		} catch (IOException e) {
			throw new AdoptionException(name() + " could not restore " + memory + " from " + backup, e);
		}
	}

	/**
	 * The CLI's own transcript is carried into the failure, because a run that exits
	 * cleanly without writing the file has said why in it — it declined, it asked a
	 * question headless mode could not answer, it wrote somewhere else — and
	 * {@link #runOrFail} only reports the output of a command that failed.
	 */
	private void requireGenerated(AdoptionContext context, CommandResult result) {
		if (!Files.isRegularFile(context.repositoryDirectory().resolve(CLAUDE_MD))) {
			throw new AdoptionException(name() + " completed but " + CLAUDE_MD + " was not found in "
					+ context.repositoryDirectory() + ". " + CommandResult.describe(claudeCommand)
					+ " exited " + result.exitCode() + " and said:" + System.lineSeparator()
					+ result.redactedOutput().strip());
		}
	}
}
