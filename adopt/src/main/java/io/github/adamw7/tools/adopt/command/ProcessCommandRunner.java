package io.github.adamw7.tools.adopt.command;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Collectors;

import io.github.adamw7.tools.adopt.AdoptionException;

/**
 * {@link CommandRunner} backed by {@link ProcessBuilder}. Standard error is
 * merged into standard output so a caller gets a single, ordered transcript of
 * what the command printed, and the process is always waited for so no child is
 * left running.
 */
public class ProcessCommandRunner implements CommandRunner {

	@Override
	public CommandResult run(Path workingDirectory, List<String> command) {
		ProcessBuilder builder = new ProcessBuilder(command)
				.directory(workingDirectory.toFile())
				.redirectErrorStream(true);
		return execute(builder, command);
	}

	private CommandResult execute(ProcessBuilder builder, List<String> command) {
		try {
			Process process = builder.start();
			String output = readOutput(process);
			int exitCode = process.waitFor();
			return new CommandResult(command, exitCode, output);
		} catch (IOException e) {
			throw new AdoptionException("Could not start command: " + String.join(" ", command), e);
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			throw new AdoptionException("Interrupted while running: " + String.join(" ", command), e);
		}
	}

	private String readOutput(Process process) throws IOException {
		try (BufferedReader reader = new BufferedReader(
				new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
			return reader.lines().collect(Collectors.joining(System.lineSeparator()));
		}
	}
}
