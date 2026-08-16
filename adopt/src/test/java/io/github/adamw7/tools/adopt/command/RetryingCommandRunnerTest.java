package io.github.adamw7.tools.adopt.command;

import static io.github.adamw7.tools.test.ExpectedFailures.assertFailure;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import io.github.adamw7.tools.adopt.AdoptionException;

class RetryingCommandRunnerTest {

	/** A transcript {@link TransientFailures} recognises, so the retry is the thing under test. */
	private static final String REFUSED = "fatal: unable to access 'https://github.com/o/r.git/':"
			+ " Failed to connect to github.com port 443: Connection timed out";

	private static final List<String> CLONE = List.of("git", "clone", "https://github.com/o/r.git", "/tmp/r");

	private final Path directory = Path.of(".");
	private final List<Duration> waits = new ArrayList<>();

	@Test
	void runsOnceWhenTheCommandSucceeds() {
		RecordingCommandRunner delegate = new RecordingCommandRunner();

		CommandResult result = retrying(delegate, 2).run(directory, CLONE);

		assertEquals(0, result.exitCode());
		assertEquals(1, delegate.count());
		assertEquals(List.of(), waits);
	}

	@Test
	void stopsRetryingAsSoonAsAnAttemptSucceeds() {
		RecordingCommandRunner delegate = failingTimes(1);

		CommandResult result = retrying(delegate, 2).run(directory, CLONE);

		assertEquals(0, result.exitCode());
		assertEquals(2, delegate.count());
	}

	/**
	 * The failure the caller is handed is the last attempt's, so a step reports what
	 * the network said rather than that a retry ran out.
	 */
	@Test
	void answersTheLastFailureOnceTheRetriesAreSpent() {
		RecordingCommandRunner delegate = RecordingCommandRunner.failing(128, REFUSED);

		CommandResult result = retrying(delegate, 2).run(directory, CLONE);

		assertEquals(128, result.exitCode());
		assertEquals(REFUSED, result.output());
		assertEquals(3, delegate.count());
	}

	@Test
	void waitsLongerBeforeEachAttempt() {
		retrying(RecordingCommandRunner.failing(128, REFUSED), 3).run(directory, CLONE);

		assertEquals(List.of(Duration.ofSeconds(2), Duration.ofSeconds(4), Duration.ofSeconds(8)), waits);
	}

	/**
	 * An operator may ask for as many attempts as {@code --retries} allows, and the
	 * doubling would otherwise leave the last of them idling for minutes.
	 */
	@Test
	void capsTheWaitHoweverManyAttemptsAreAllowed() {
		retrying(RecordingCommandRunner.failing(128, REFUSED), 8).run(directory, CLONE);

		assertEquals(RetryingCommandRunner.MAX_BACKOFF, waits.get(waits.size() - 1));
		assertTrue(waits.stream().allMatch(wait -> wait.compareTo(RetryingCommandRunner.MAX_BACKOFF) <= 0),
				"no wait may exceed the cap: " + waits);
	}

	@Test
	void doesNotRetryAFailureTheNetworkDidNotCause() {
		RecordingCommandRunner delegate = RecordingCommandRunner.failing(1, "remote: Repository not found.");

		CommandResult result = retrying(delegate, 2).run(directory, CLONE);

		assertEquals(1, result.exitCode());
		assertEquals(1, delegate.count());
	}

	@Test
	void passesThroughWhenNoRetriesAreAllowed() {
		RecordingCommandRunner delegate = RecordingCommandRunner.failing(128, REFUSED);

		CommandResult result = retrying(delegate, 0).run(directory, CLONE);

		assertEquals(128, result.exitCode());
		assertEquals(1, delegate.count());
		assertEquals(List.of(), waits);
	}

	@Test
	void handsTheDelegateTheWorkingDirectoryAndCommandUnchanged() {
		RecordingCommandRunner delegate = failingTimes(1);

		retrying(delegate, 1).run(directory, CLONE);

		assertEquals(CLONE, delegate.commandAt(1));
		assertEquals(directory, delegate.invocations().get(1).workingDirectory());
	}

	@Test
	void rejectsANegativeRetryCount() {
		assertFailure(IllegalArgumentException.class,
				() -> new RetryingCommandRunner(new RecordingCommandRunner(), -1), "retries must not be negative");
	}

	/**
	 * A host shutting its worker down — an MCP server, a cancelled CI job — is obeyed
	 * while the run is waiting to try again, rather than being made to wait out the
	 * backoff first.
	 */
	@Test
	void interruptionDuringTheWaitAbortsAndRestoresTheInterruptFlag() {
		CommandRunner runner = new RetryingCommandRunner(RecordingCommandRunner.failing(128, REFUSED), 1);
		Thread.currentThread().interrupt();
		try {
			assertThrows(AdoptionException.class, () -> runner.run(directory, CLONE));
			assertTrue(Thread.currentThread().isInterrupted(), "the interrupt flag must be restored");
		} finally {
			Thread.interrupted();
		}
	}

	private RetryingCommandRunner retrying(CommandRunner delegate, int retries) {
		return new RetryingCommandRunner(delegate, retries, waits::add);
	}

	/** A runner refused by the network the first {@code failures} times, and answering after that. */
	private RecordingCommandRunner failingTimes(int failures) {
		int[] attempts = { 0 };
		return new RecordingCommandRunner(command -> ++attempts[0] <= failures
				? new CommandResult(command, 128, REFUSED)
				: new CommandResult(command, 0, ""));
	}
}
