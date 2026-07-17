package io.github.adamw7.tools.adopt.step;

import java.nio.file.Path;
import java.util.List;

import io.github.adamw7.tools.adopt.AdoptionException;
import io.github.adamw7.tools.adopt.command.CommandResult;
import io.github.adamw7.tools.adopt.command.CommandRunner;

/**
 * Base for steps that shell out through a {@link CommandRunner}, sharing the
 * run-and-fail-fast behaviour: a command that exits non-zero aborts the
 * adoption with an {@link AdoptionException} carrying the command transcript.
 */
public abstract class AbstractCommandStep implements AdoptionStep {

	protected final CommandResult runOrFail(CommandRunner runner, Path workingDirectory, List<String> command) {
		CommandResult result = runner.run(workingDirectory, command);
		if (!result.succeeded()) {
			throw new AdoptionException(
					name() + " failed (exit " + result.exitCode() + ") running: " + result.describe()
							+ System.lineSeparator() + result.output());
		}
		return result;
	}
}
