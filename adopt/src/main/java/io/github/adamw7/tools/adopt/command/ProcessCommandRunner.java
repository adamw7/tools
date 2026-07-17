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
 * cannot block the adoption forever: on expiry the process is destroyed and the
 * failure is reported with whatever output was captured so far.
 */
public class ProcessCommandRunner implements CommandRunner {

	static final Duration DEFAULT_TIMEOUT = Duration.ofMinutes(10);

	private final Duration timeout;

	public ProcessCommandRunner() {
		this(DEFAULT_TIMEOUT);
	}

	public ProcessCommandRunner(Duration timeout) {
		this.timeout = requirePositive(timeout);
	}

	private static Duration requirePositive(Duration timeout) {
		if (timeout == null || timeout.isNegative() || timeout.isZero()) {
			throw new IllegalArgumentException("timeout must be positive");
		}
		return timeout;
	}

	@Override
	public CommandResult run(Path workingDirectory, List<String> command) {
		ProcessBuilder builder = new ProcessBuilder(command)
				.directory(workingDirectory.toFile())
				.redirectErrorStream(true);
		return execute(builder, command);
	}

	private CommandResult execute(ProcessBuilder builder, List<String> command) {
		Process process = start(builder, command);
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

	private CommandResult await(Process process, List<String> command, StreamGobbler output) {
		try {
			if (process.waitFor(timeout.toMillis(), TimeUnit.MILLISECONDS)) {
				return new CommandResult(command, process.exitValue(), output.output());
			}
			throw timedOut(process, command, output);
		} catch (InterruptedException e) {
			process.destroyForcibly();
			Thread.currentThread().interrupt();
			throw new AdoptionException("Interrupted while running: " + String.join(" ", command), e);
		}
	}

	private AdoptionException timedOut(Process process, List<String> command, StreamGobbler output) {
		process.destroyForcibly();
		return new AdoptionException("Timed out after " + timeout + " running: " + String.join(" ", command)
				+ System.lineSeparator() + output.output());
	}
}
