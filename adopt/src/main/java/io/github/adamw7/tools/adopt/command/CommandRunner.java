package io.github.adamw7.tools.adopt.command;

import java.nio.file.Path;
import java.util.List;

/**
 * Runs an external command in a given working directory and reports its result.
 * Abstracting the process invocation behind this interface keeps every
 * adoption step free of {@link ProcessBuilder} details and lets tests drive the
 * steps with a recording stub instead of spawning real processes.
 */
public interface CommandRunner {

	CommandResult run(Path workingDirectory, List<String> command);
}
