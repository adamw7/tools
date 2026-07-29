package io.github.adamw7.tools.adopt.command;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import java.util.function.Predicate;

/**
 * Test {@link CommandRunner} that records every invocation and answers each one
 * through a configurable strategy, so step tests can assert what was run and
 * simulate success or failure without spawning a real process.
 */
public class RecordingCommandRunner implements CommandRunner {

	public record Invocation(Path workingDirectory, List<String> command) {
	}

	private final List<Invocation> invocations = new ArrayList<>();
	private final Function<List<String>, CommandResult> strategy;

	public RecordingCommandRunner() {
		this(command -> new CommandResult(command, 0, ""));
	}

	public RecordingCommandRunner(Function<List<String>, CommandResult> strategy) {
		this.strategy = strategy;
	}

	/** A runner whose every command succeeds, answering the transcript a test wants read back. */
	public static RecordingCommandRunner answering(String output) {
		return answering(0, output);
	}

	/**
	 * A runner whose every command fails the same way — the shape a test takes when
	 * it is about how a step reports a failure rather than about which command failed.
	 */
	public static RecordingCommandRunner failing(int exitCode, String output) {
		return answering(exitCode, output);
	}

	private static RecordingCommandRunner answering(int exitCode, String output) {
		return new RecordingCommandRunner(command -> new CommandResult(command, exitCode, output));
	}

	/**
	 * A runner that succeeds except for the commands the predicate picks out, so a
	 * test can fail exactly the one step it is about and let the rest of the pipeline
	 * run.
	 */
	public static RecordingCommandRunner failingWhen(Predicate<List<String>> failing, int exitCode, String output) {
		return new RecordingCommandRunner(command -> failing.test(command)
				? new CommandResult(command, exitCode, output)
				: new CommandResult(command, 0, ""));
	}

	/** The common case of {@link #failingWhen}: the command contains this word. */
	public static RecordingCommandRunner failingOn(String word, int exitCode, String output) {
		return failingWhen(command -> command.contains(word), exitCode, output);
	}

	@Override
	public CommandResult run(Path workingDirectory, List<String> command) {
		invocations.add(new Invocation(workingDirectory, command));
		return strategy.apply(command);
	}

	public List<Invocation> invocations() {
		return List.copyOf(invocations);
	}

	public List<String> commandAt(int index) {
		return invocations.get(index).command();
	}

	public int count() {
		return invocations.size();
	}
}
