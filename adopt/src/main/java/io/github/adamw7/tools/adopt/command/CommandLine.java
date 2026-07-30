package io.github.adamw7.tools.adopt.command;

import java.util.ArrayList;
import java.util.List;

/**
 * Builds the argument list of one command to hand to a {@link CommandRunner}.
 *
 * <p>Nearly every adoption step assembles its command the same way: a fixed
 * program and its leading arguments, then arguments that depend on the run — a
 * {@code --repo} the URL may not name, a {@code --draft} only a draft pull request
 * carries, a {@code --reviewer} per requested reviewer. Written out directly that
 * is a mutable list seeded from a {@link List#of} literal, a run of {@code add}
 * calls guarded by {@code if}s and {@code forEach}es, and a closing
 * {@link List#copyOf} — the same six lines in every step, with the command itself
 * buried in them.
 *
 * <p>Collecting it here leaves each step naming only its own arguments, and makes
 * the immutable result the single way to finish rather than something each step
 * has to remember.
 */
public final class CommandLine {

	private final List<String> arguments = new ArrayList<>();

	private CommandLine() {
	}

	/** Starts a command with its program name and any arguments it always carries. */
	public static CommandLine of(String... program) {
		return new CommandLine().add(program);
	}

	/** Appends {@code arguments} in order. */
	public CommandLine add(String... arguments) {
		this.arguments.addAll(List.of(arguments));
		return this;
	}

	/** Appends an already-assembled run of arguments in order. */
	public CommandLine addAll(List<String> arguments) {
		this.arguments.addAll(arguments);
		return this;
	}

	/** Appends {@code arguments} only when {@code condition} holds, e.g. an optional flag. */
	public CommandLine addIf(boolean condition, String... arguments) {
		return condition ? add(arguments) : this;
	}

	/**
	 * Appends {@code flag} followed by its value once per entry of {@code values},
	 * which is how a repeatable option such as {@code --reviewer} is written. An
	 * empty list leaves the command untouched.
	 */
	public CommandLine addEach(String flag, List<String> values) {
		values.forEach(value -> add(flag, value));
		return this;
	}

	/** The assembled command, as an immutable list. */
	public List<String> toList() {
		return List.copyOf(arguments);
	}
}
