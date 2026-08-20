package io.github.adamw7.tools.adopt.command;

import java.util.List;

import io.github.adamw7.tools.secret.Redaction;

/**
 * Outcome of running an external command: the exit code and the combined
 * standard-output/standard-error text the command produced. The originating
 * command is kept so failures can be reported without the caller having to
 * remember what it asked for.
 *
 * @param command  the command that was run, program first, as it was handed to
 *                 the runner — credentials included, so anything reporting it
 *                 goes through {@link #describe()}
 * @param exitCode the code the command exited with, zero meaning success
 * @param output   the command's standard output and standard error, merged into
 *                 one ordered transcript — the tool's own text, so anything that
 *                 outlives the run reports it through {@link #redactedOutput()}
 */
public record CommandResult(List<String> command, int exitCode, String output) {

	public boolean succeeded() {
		return exitCode == 0;
	}

	public String describe() {
		return describe(command);
	}

	/**
	 * The transcript with any clone credentials masked, for the logs, failure messages
	 * and JSON report a secret must not reach. A tool handed a credentialled URL echoes
	 * it back when it cannot use it, so masking here rather than at each caller keeps
	 * it out of the places a caller would forget.
	 */
	public String redactedOutput() {
		return Redaction.of(output);
	}

	/**
	 * The command as it would be typed, for the failures raised before there is a
	 * result to describe — a process that could not be started, or one destroyed on
	 * its timeout — so every report of a command reads the same way. A clone URL's
	 * credentials are masked here, at the one place a command is turned into text,
	 * so no caller can report the arguments verbatim by forgetting to.
	 */
	public static String describe(List<String> command) {
		return Redaction.of(String.join(" ", command));
	}
}
