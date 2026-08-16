package io.github.adamw7.tools.adopt.command;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.TimeUnit;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import io.github.adamw7.tools.adopt.AdoptionException;
import io.github.adamw7.tools.adopt.Elapsed;
import io.github.adamw7.tools.adopt.Redaction;

/**
 * {@link CommandRunner} backed by {@link ProcessBuilder}. Standard error is
 * merged into standard output so a caller gets a single, ordered transcript.
 * Every command is bounded by a timeout so a hung child — a stalled
 * {@code git clone}, a stuck {@code claude} — cannot block the adoption forever:
 * on expiry the whole process tree is destroyed and the failure is reported with
 * whatever output was captured.
 *
 * <p>The child's standard input is closed as soon as it starts, so a tool that
 * reads from it — a {@code git} or {@code gh} credential prompt — sees
 * end-of-stream and fails fast instead of blocking until the timeout kills it.
 *
 * <p>Every command is traced at debug — what was run, where, how it exited and
 * how long it took — because this is the one place the adoption learns anything
 * from the outside world, and the steps above it report only the commands that
 * stopped the run. A tolerated failure or a step that decided it had nothing to do
 * leaves no trace anywhere else, so a run that did less than it was expected to
 * reads exactly like one that did everything.
 */
public class ProcessCommandRunner implements CommandRunner {

	private static final Logger log = LogManager.getLogger(ProcessCommandRunner.class);

	/**
	 * How long a command may run when the caller names no timeout of its own. Sized
	 * for the longest command the pipeline runs — a {@code claude init} over a whole
	 * repository — rather than for a {@code git rev-parse}. Public because the run's
	 * {@link io.github.adamw7.tools.adopt.AdoptionOptions} answers it as the default
	 * an operator's {@code --timeout} overrides.
	 */
	public static final Duration DEFAULT_TIMEOUT = Duration.ofMinutes(10);

	private final Duration timeout;
	private final ExecutableResolver resolver;

	public ProcessCommandRunner() {
		this(DEFAULT_TIMEOUT);
	}

	public ProcessCommandRunner(Duration timeout) {
		this.timeout = requirePositive(timeout);
		this.resolver = new ExecutableResolver();
	}

	private static Duration requirePositive(Duration timeout) {
		if (timeout == null || timeout.isNegative() || timeout.isZero()) {
			throw new IllegalArgumentException("timeout must be positive");
		}
		return timeout;
	}

	/**
	 * The bound every command started here is given. Package-visible so
	 * {@link CommandRunners} can be asserted to have carried the run's own
	 * {@code --timeout} down to the runner that enforces it: the only other way this
	 * value shows is a command being destroyed on it, so a run bounded by the default
	 * when the operator asked for something else reads exactly like one that was
	 * configured correctly, until a command outlives what was actually set.
	 */
	Duration timeout() {
		return timeout;
	}

	@Override
	public CommandResult run(Path workingDirectory, List<String> command) {
		log.debug("Running in {}: {}", workingDirectory, CommandResult.describe(command));
		Elapsed elapsed = Elapsed.started();
		ProcessBuilder builder = new ProcessBuilder(resolver.resolve(command))
				.directory(workingDirectory.toFile())
				.redirectErrorStream(true);
		Process process = start(builder, command);
		CommandResult result = awaitOrDestroy(process, command);
		logOutcome(result, elapsed);
		return result;
	}

	/**
	 * The transcript of a command that exited non-zero is logged here as well as being
	 * handed back, because whether it reaches a message at all is the caller's
	 * decision: a step running through
	 * {@link io.github.adamw7.tools.adopt.step.AbstractCommandStep#runTolerating}
	 * discards a tolerated failure whole, along with the tool's own account of what it
	 * refused to do. Redacting it is not optional — a tool handed a credentialled
	 * clone URL echoes it back — and the guard keeps that scan off the path a disabled
	 * log takes.
	 */
	private void logOutcome(CommandResult result, Elapsed elapsed) {
		log.debug("Exited {} after {}: {}", result.exitCode(), elapsed, result.describe());
		if (!result.succeeded() && log.isDebugEnabled()) {
			log.debug("Output of the failed command:{}{}", System.lineSeparator(), result.redactedOutput());
		}
	}

	/**
	 * Nothing may leave this method with the child still running. {@link #await}
	 * destroys the tree on the two failures it raises itself, but the steps before it
	 * — closing standard input, starting the reader — can fail too, and a child left
	 * behind by one of those outlives the adoption entirely: no later step knows it
	 * exists, and its merged output pipe stays open with nobody draining it.
	 */
	private CommandResult awaitOrDestroy(Process process, List<String> command) {
		try {
			closeStandardInput(process, command);
			return await(process, command, StreamGobbler.consuming(process.getInputStream()));
		} catch (RuntimeException e) {
			destroyTree(process);
			throw e;
		}
	}

	private Process start(ProcessBuilder builder, List<String> command) {
		try {
			return builder.start();
		} catch (IOException e) {
			throw new AdoptionException("Could not start command: " + CommandResult.describe(command), e);
		}
	}

	private void closeStandardInput(Process process, List<String> command) {
		try {
			process.getOutputStream().close();
		} catch (IOException e) {
			throw new AdoptionException("Could not close standard input for: " + CommandResult.describe(command), e);
		}
	}

	private CommandResult await(Process process, List<String> command, StreamGobbler output) {
		try {
			if (process.waitFor(timeout.toMillis(), TimeUnit.MILLISECONDS)) {
				return new CommandResult(command, process.exitValue(), output.output());
			}
			throw timedOut(process, command, output);
		} catch (InterruptedException e) {
			destroyTree(process);
			Thread.currentThread().interrupt();
			throw new AdoptionException("Interrupted while running: " + CommandResult.describe(command), e);
		}
	}

	private AdoptionException timedOut(Process process, List<String> command, StreamGobbler output) {
		destroyTree(process);
		return new AdoptionException("Timed out after " + timeout + " running: " + CommandResult.describe(command)
				+ System.lineSeparator() + Redaction.of(output.output()));
	}

	/**
	 * Destroys the process together with every descendant it spawned.
	 * {@link Process#destroyForcibly()} kills only the direct child; a helper or
	 * daemon it forked can outlive it and keep the merged output pipe open, which
	 * would otherwise stop the stream from ever reaching end-of-stream.
	 */
	private void destroyTree(Process process) {
		process.descendants().forEach(ProcessHandle::destroyForcibly);
		process.destroyForcibly();
	}
}
