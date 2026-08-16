package io.github.adamw7.tools.adopt.command;

import io.github.adamw7.tools.adopt.AdoptionOptions;

/**
 * Assembles the toolchain a real run is driven through: a
 * {@link ProcessCommandRunner} bounded by the run's timeout, wrapped in a
 * {@link RetryingCommandRunner} that gives a {@code git} or {@code gh} the network
 * refused the further attempts the run allows.
 *
 * <p>Said once because both entry points need it and neither may answer it
 * differently — the same reason {@link AdoptionOptions} groups what a run is
 * configured with. A command line and an MCP call that assembled their own runners
 * would have been one edit away from a {@code --timeout} the server honours and an
 * argument the command line quietly drops, and nothing downstream reports a
 * decorator that was left off: the run simply stops retrying.
 */
public final class CommandRunners {

	private CommandRunners() {
	}

	/**
	 * A runner configured with no retries is wrapped all the same, because the
	 * decorator is then a pass-through and the alternative is two ways of building
	 * the same toolchain — the very thing this method exists to prevent.
	 *
	 * @param options how the run is configured, supplying the per-command timeout and
	 *                the number of further attempts a refused command earns
	 * @return the runner every step of the run shells out through
	 */
	public static CommandRunner forRun(AdoptionOptions options) {
		return new RetryingCommandRunner(new ProcessCommandRunner(options.commandTimeout()), options.retries());
	}
}
