package io.github.adamw7.tools.adopt.command;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.TimeUnit;

import io.github.adamw7.tools.adopt.AdoptionException;

/**
 * {@link CommandRunner} backed by {@link ProcessBuilder}. Standard error is
 * merged into standard output so a caller gets a single, ordered transcript of
 * what the command printed. Every command is bounded by a timeout so a hung
 * child — a stalled {@code git clone} or a stuck {@code claude} invocation —
 * cannot block the adoption forever: on expiry the whole process tree is
 * destroyed and the failure is reported with whatever output was captured so
 * far.
 *
 * <p>The child's standard input is closed as soon as it starts, so a tool that
 * reads from it — a {@code git} credential prompt, a {@code gh} authentication
 * prompt — sees end-of-stream and fails fast instead of blocking on an input
 * that never arrives until the timeout kills it.
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
		return execute(builder, command);
	}

	private CommandResult execute(ProcessBuilder builder, List<String> command) {
		Process process = start(builder, command);
		closeStandardInput(process, command);
		StreamGobbler output = StreamGobbler.consuming(process.getInputStream());
		return await(process, command, output);
	}

	private Process start(ProcessBuilder builder, List<String> command) {
		try {
			return builder.start();
		} catch (IOException e) {
			throw new AdoptionException("Could not start command: " + String.join(" ", command), e);
		}
	}

	private void closeStandardInput(Process process, List<String> command) {
		try {
			process.getOutputStream().close();
		} catch (IOException e) {
			throw new AdoptionException("Could not close standard input for: " + String.join(" ", command), e);
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
			throw new AdoptionException("Interrupted while running: " + String.join(" ", command), e);
		}
	}

	private AdoptionException timedOut(Process process, List<String> command, StreamGobbler output) {
		destroyTree(process);
		return new AdoptionException("Timed out after " + timeout + " running: " + String.join(" ", command)
				+ System.lineSeparator() + output.output());
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
