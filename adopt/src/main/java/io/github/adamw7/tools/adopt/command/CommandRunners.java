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
 * configured with. Assembling their own runners would leave them one edit away from
 * a {@code --timeout} one honours and the other drops, and nothing downstream
 * reports a decorator left off: the run simply stops retrying.
 */
public final class CommandRunners {

	private CommandRunners() {
	}

	/**
	 * A runner configured with no retries is wrapped all the same: the decorator is
	 * then a pass-through, and the alternative is two ways of building one toolchain.
	 *
	 * @param options how the run is configured, supplying the per-command timeout and
	 *                the number of further attempts a refused command earns
	 * @return the runner every step of the run shells out through
	 */
	public static CommandRunner forRun(AdoptionOptions options) {
		return new RetryingCommandRunner(new ProcessCommandRunner(options.commandTimeout()), options.retries());
	}
}
