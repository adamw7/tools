package io.github.adamw7.tools.adopt.command;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

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
