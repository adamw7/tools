package io.github.adamw7.tools.adopt.command;

import java.util.List;

/**
 * Outcome of running an external command: the exit code and the combined
 * standard-output/standard-error text the command produced. The originating
 * command is kept so failures can be reported without the caller having to
 * remember what it asked for.
 */
public record CommandResult(List<String> command, int exitCode, String output) {

	public boolean succeeded() {
		return exitCode == 0;
	}

	public String describe() {
		return describe(command);
	}

	/**
	 * The command as it would be typed, for the failures raised before there is a
	 * result to describe — a process that could not be started, or one destroyed on
	 * its timeout — so every report of a command reads the same way.
	 */
	public static String describe(List<String> command) {
		return String.join(" ", command);
	}
}
