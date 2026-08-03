package io.github.adamw7.tools.adopt.step;

import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

import io.github.adamw7.tools.adopt.AdoptionException;
import io.github.adamw7.tools.adopt.command.CommandResult;
import io.github.adamw7.tools.adopt.command.CommandRunner;

/**
 * Base for steps that shell out through a {@link CommandRunner}, sharing the
 * run-and-fail-fast behaviour: a command that exits non-zero aborts the
 * adoption with an {@link AdoptionException} carrying the command transcript.
 * A step whose tool reports an already-satisfied request as a failure instead of
 * a no-op runs through {@link #runTolerating} rather than repeating that
 * judgement for itself.
 */
public abstract class AbstractCommandStep implements AdoptionStep {

	protected final CommandResult runOrFail(CommandRunner runner, Path workingDirectory, List<String> command) {
		CommandResult result = runner.run(workingDirectory, command);
		if (!result.succeeded()) {
			throw failure(result);
		}
		return result;
	}

	/**
	 * Runs the command, treating a failure whose transcript reports one of
	 * {@code toleratedFailures} as a no-op rather than an aborted adoption — the
	 * pipeline is re-runnable, so a tool refusing to do again what it has already
	 * done is the expected outcome of a second run, not an error.
	 *
	 * @param toleratedFailures fragments of the tool's own wording, matched
	 *                          case-insensitively against the transcript
	 * @return the result, or empty when the command failed in a tolerated way
	 */
	protected final Optional<CommandResult> runTolerating(CommandRunner runner, Path workingDirectory,
			List<String> command, List<String> toleratedFailures) {
		CommandResult result = runner.run(workingDirectory, command);
		if (result.succeeded()) {
			return Optional.of(result);
		}
		if (reports(result, toleratedFailures)) {
			return Optional.empty();
		}
		throw failure(result);
	}

	private boolean reports(CommandResult result, List<String> toleratedFailures) {
		String output = result.output().toLowerCase(Locale.ROOT);
		return toleratedFailures.stream().map(tolerated -> tolerated.toLowerCase(Locale.ROOT)).anyMatch(output::contains);
	}

	/**
	 * The transcript is redacted as well as the command, because a tool that fails
	 * on a credentialled clone URL echoes it back — {@code fatal: could not read
	 * Username for 'https://...@github.com'} — and this message becomes the run's
	 * reported failure.
	 */
	private AdoptionException failure(CommandResult result) {
		return new AdoptionException(name() + " failed (exit " + result.exitCode() + ") running: " + result.describe()
				+ System.lineSeparator() + result.redactedOutput());
	}
}
