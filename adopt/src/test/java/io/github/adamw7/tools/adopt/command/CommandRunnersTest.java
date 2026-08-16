package io.github.adamw7.tools.adopt.command;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

import java.time.Duration;

import org.junit.jupiter.api.Test;

import io.github.adamw7.tools.adopt.AdoptionOptions;
import io.github.adamw7.tools.adopt.step.PullRequestOptions;

/**
 * The toolchain a real run is driven through is assembled in one place so the
 * command line and the MCP tool cannot answer {@code --timeout} or
 * {@code --retries} differently. An ArchUnit rule keeps either entry point from
 * assembling one of its own, but that rule only says who may build the toolchain,
 * never what it has to come out as — and every way this assembly can go wrong is
 * silent at run time. A missing decorator behaves exactly like a network that
 * never faltered, and a timeout that did not arrive shows only when a command
 * outlives the bound nobody set.
 */
class CommandRunnersTest {

	/**
	 * A number that is neither of the two the run would otherwise end up with — not
	 * the default, and not the zero an unwired {@code int} field carries — so the
	 * assertion fails on a value that was defaulted as loudly as on one dropped.
	 */
	private static final int RETRIES = 4;

	/** Likewise distinct from {@link ProcessCommandRunner#DEFAULT_TIMEOUT}. */
	private static final Duration TIMEOUT = Duration.ofMinutes(3);

	@Test
	void retriesOnTopOfTheRunnerThatSpawnsTheProcess() {
		CommandRunner runner = CommandRunners.forRun(options(TIMEOUT, RETRIES));

		RetryingCommandRunner retrying = assertInstanceOf(RetryingCommandRunner.class, runner);
		assertInstanceOf(ProcessCommandRunner.class, retrying.delegate());
	}

	@Test
	void carriesTheRunsTimeoutToTheRunnerThatEnforcesIt() {
		CommandRunner runner = CommandRunners.forRun(options(TIMEOUT, RETRIES));

		assertEquals(TIMEOUT, processRunnerIn(runner).timeout());
	}

	@Test
	void carriesTheRunsRetriesToTheDecoratorThatServesThem() {
		CommandRunner runner = CommandRunners.forRun(options(TIMEOUT, RETRIES));

		assertEquals(RETRIES, retryingRunnerIn(runner).retries());
	}

	/**
	 * A run allowing no retries is wrapped all the same, so the decorator is a
	 * pass-through rather than there being a second way to build the toolchain — the
	 * very drift assembling it in one place exists to prevent.
	 */
	@Test
	void wrapsEvenWhenTheRunAllowsNoRetries() {
		CommandRunner runner = CommandRunners.forRun(options(TIMEOUT, 0));

		assertEquals(0, retryingRunnerIn(runner).retries());
	}

	/**
	 * A run that names no timeout is bounded by the runner's own default rather than
	 * by nothing, which is what {@link AdoptionOptions} substitutes for the absent
	 * value and what this assembly has to pass on unchanged.
	 */
	@Test
	void boundsARunThatNamesNoTimeoutByTheDefault() {
		CommandRunner runner = CommandRunners.forRun(AdoptionOptions.defaults());

		assertEquals(ProcessCommandRunner.DEFAULT_TIMEOUT, processRunnerIn(runner).timeout());
	}

	@Test
	void servesTheDefaultRetriesForARunThatNamesNone() {
		CommandRunner runner = CommandRunners.forRun(AdoptionOptions.defaults());

		assertEquals(AdoptionOptions.DEFAULT_RETRIES, retryingRunnerIn(runner).retries());
	}

	private RetryingCommandRunner retryingRunnerIn(CommandRunner runner) {
		return assertInstanceOf(RetryingCommandRunner.class, runner);
	}

	private ProcessCommandRunner processRunnerIn(CommandRunner runner) {
		return assertInstanceOf(ProcessCommandRunner.class, retryingRunnerIn(runner).delegate());
	}

	private AdoptionOptions options(Duration commandTimeout, int retries) {
		return new AdoptionOptions(PullRequestOptions.defaults(), false, null, false, commandTimeout, retries);
	}
}
