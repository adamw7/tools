package io.github.adamw7.tools.adopt.command;

import static io.github.adamw7.tools.test.ExpectedFailures.assertFailure;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.time.Duration;
import java.util.List;

import org.junit.jupiter.api.Test;

import io.github.adamw7.tools.adopt.AdoptionException;

class ProcessCommandRunnerTest {

	private final ProcessCommandRunner runner = new ProcessCommandRunner();

	@Test
	void capturesExitCodeAndOutput() {
		CommandResult result = runner.run(Path.of("."), PlatformCommands.printing("hello"));
		assertEquals(0, result.exitCode());
		assertEquals("hello" + System.lineSeparator(), result.output());
	}

	@Test
	void reportsNonZeroExitCode() {
		CommandResult result = runner.run(Path.of("."), PlatformCommands.exitingWith(3));
		assertEquals(3, result.exitCode());
	}

	@Test
	void mergesStandardErrorIntoOutput() {
		CommandResult result = runner.run(Path.of("."), PlatformCommands.printingToStandardError("oops"));
		assertTrue(result.output().contains("oops"));
	}

	@Test
	void rejectsATimeoutThatIsNotPositive() {
		assertThrows(IllegalArgumentException.class, () -> new ProcessCommandRunner(Duration.ZERO));
		assertThrows(IllegalArgumentException.class, () -> new ProcessCommandRunner(Duration.ofSeconds(-1)));
		assertThrows(IllegalArgumentException.class, () -> new ProcessCommandRunner(null));
	}

	/**
	 * An adoption running inside a host that interrupts its worker — an MCP server
	 * shutting down — must abort rather than swallow the interrupt, and must leave
	 * the flag set so the caller above still sees it.
	 */
	@Test
	void interruptionAbortsAndRestoresTheInterruptFlag() {
		Thread.currentThread().interrupt();
		try {
			assertThrows(AdoptionException.class,
					() -> runner.run(Path.of("."), PlatformCommands.sleepingFor(30)));
			assertTrue(Thread.currentThread().isInterrupted(), "the interrupt flag must be restored");
		} finally {
			Thread.interrupted();
		}
	}

	@Test
	void wrapsMissingExecutableInAdoptionException() {
		assertThrows(AdoptionException.class,
				() -> runner.run(Path.of("."), List.of("definitely-not-a-real-binary-xyz")));
	}

	@Test
	void killsAndReportsACommandThatOutlivesTheTimeout() {
		ProcessCommandRunner impatient = new ProcessCommandRunner(Duration.ofMillis(100));
		assertFailure(AdoptionException.class, () -> impatient.run(Path.of("."), PlatformCommands.sleepingFor(30)),
				"Timed out");
	}

	@Test
	void capturesOutputWhenTheCommandFinishesWithinTheTimeout() {
		ProcessCommandRunner patient = new ProcessCommandRunner(Duration.ofSeconds(30));
		CommandResult result = patient.run(Path.of("."), PlatformCommands.printing("quick"));
		assertEquals(0, result.exitCode());
		assertEquals("quick" + System.lineSeparator(), result.output());
	}

	@Test
	void rejectsANonPositiveTimeout() {
		assertThrows(IllegalArgumentException.class, () -> new ProcessCommandRunner(Duration.ZERO));
	}

	@Test
	void closesStandardInputSoAStdinReaderFinishesInsteadOfHanging() {
		ProcessCommandRunner patient = new ProcessCommandRunner(Duration.ofSeconds(30));
		CommandResult result = patient.run(Path.of("."), PlatformCommands.readingStandardInput());
		assertEquals(0, result.exitCode());
	}
}
