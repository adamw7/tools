package io.github.adamw7.tools.adopt.command;

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
		assertEquals("hello", result.output());
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
	void wrapsMissingExecutableInAdoptionException() {
		assertThrows(AdoptionException.class,
				() -> runner.run(Path.of("."), List.of("definitely-not-a-real-binary-xyz")));
	}

	@Test
	void killsAndReportsACommandThatOutlivesTheTimeout() {
		ProcessCommandRunner impatient = new ProcessCommandRunner(Duration.ofMillis(100));
		AdoptionException thrown = assertThrows(AdoptionException.class,
				() -> impatient.run(Path.of("."), PlatformCommands.sleepingFor(30)));
		assertTrue(thrown.getMessage().contains("Timed out"), thrown.getMessage());
	}

	@Test
	void capturesOutputWhenTheCommandFinishesWithinTheTimeout() {
		ProcessCommandRunner patient = new ProcessCommandRunner(Duration.ofSeconds(30));
		CommandResult result = patient.run(Path.of("."), PlatformCommands.printing("quick"));
		assertEquals(0, result.exitCode());
		assertEquals("quick", result.output());
	}

	@Test
	void rejectsANonPositiveTimeout() {
		assertThrows(IllegalArgumentException.class, () -> new ProcessCommandRunner(Duration.ZERO));
	}
}
