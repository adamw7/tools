package io.github.adamw7.tools.enforcer.e2e;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Runs one external process for the end-to-end tests and answers what it did. Both
 * things this package forks — the {@link MavenBuild} that meets a rule the way a
 * contributor does, and the {@link GitClone} that fetches a project this repository
 * did not write — need the same four guarantees, and each used to spell them out
 * again:
 * <ul>
 * <li>output is merged and redirected to a temporary log <em>outside</em> the
 * directory the process runs in, because a fixture build must never leave a file in
 * the project it was pointed at;</li>
 * <li>the wait is bounded, so a process that stops responding fails its test in
 * minutes rather than leaving surefire's fork timeout to kill the fork and take the
 * log — the only diagnosis there is — with it;</li>
 * <li>an interrupt re-sets the flag before it is reported, so a cancelled run does
 * not leave the thread looking uninterrupted; and</li>
 * <li>the log is deleted on every path, the failing one included.</li>
 * </ul>
 * The two differ only in how long they may take and in what they make of the
 * outcome, so those are what they supply.
 */
final class LoggedProcess {

	private final String logPrefix;
	private final long timeoutMinutes;

	/**
	 * @param logPrefix      names the temporary log, so a log left behind by a killed
	 *                       JVM says which kind of process wrote it
	 * @param timeoutMinutes how long the process may run before it is destroyed
	 */
	LoggedProcess(String logPrefix, long timeoutMinutes) {
		this.logPrefix = logPrefix;
		this.timeoutMinutes = timeoutMinutes;
	}

	/**
	 * Runs {@code command} in {@code directory} and answers its exit code together
	 * with everything it printed.
	 */
	BuildOutcome run(Path directory, List<String> command) {
		Path log = temporaryFile();
		try {
			Process process = new ProcessBuilder(command)
					.directory(directory.toFile())
					.redirectErrorStream(true)
					.redirectOutput(log.toFile())
					.start();
			return new BuildOutcome(exitCodeOf(process, command), read(log));
		} catch (IOException e) {
			throw new UncheckedIOException("Could not run " + command, e);
		} finally {
			delete(log);
		}
	}

	/**
	 * A process that never returns is killed here and reported as what it is, rather
	 * than hanging the whole test run.
	 */
	private int exitCodeOf(Process process, List<String> command) {
		try {
			if (!process.waitFor(timeoutMinutes, TimeUnit.MINUTES)) {
				process.destroyForcibly();
				throw new IllegalStateException(command + " did not finish within " + timeoutMinutes + " minutes");
			}
			return process.exitValue();
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			throw new IllegalStateException("Interrupted while waiting for " + command, e);
		}
	}

	/**
	 * Decoded leniently: a forked tool writes the platform's encoding, and a byte
	 * that does not decode is noise in a log, never a reason to fail a test that has
	 * an assertion of its own to make.
	 */
	private String read(Path log) {
		try {
			return new String(Files.readAllBytes(log), StandardCharsets.UTF_8);
		} catch (IOException e) {
			throw new UncheckedIOException("Could not read the process log " + log, e);
		}
	}

	private Path temporaryFile() {
		try {
			return Files.createTempFile(logPrefix, ".log");
		} catch (IOException e) {
			throw new UncheckedIOException("Could not create a log for " + logPrefix, e);
		}
	}

	/** Shared with the callers, which have temporary working directories of their own to clear up. */
	static void delete(Path path) {
		try {
			Files.deleteIfExists(path);
		} catch (IOException e) {
			throw new UncheckedIOException("Could not delete " + path, e);
		}
	}
}
