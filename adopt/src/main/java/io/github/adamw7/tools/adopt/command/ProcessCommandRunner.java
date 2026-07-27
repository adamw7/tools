package io.github.adamw7.tools.adopt.command;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.TimeUnit;

import io.github.adamw7.tools.adopt.AdoptionException;
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
 */
public class ProcessCommandRunner implements CommandRunner {

	static final Duration DEFAULT_TIMEOUT = Duration.ofMinutes(10);

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

	@Override
	public CommandResult run(Path workingDirectory, List<String> command) {
		ProcessBuilder builder = new ProcessBuilder(resolver.resolve(command))
				.directory(workingDirectory.toFile())
				.redirectErrorStream(true);
		Process process = start(builder, command);
		closeStandardInput(process, command);
		return await(process, command, StreamGobbler.consuming(process.getInputStream()));
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
