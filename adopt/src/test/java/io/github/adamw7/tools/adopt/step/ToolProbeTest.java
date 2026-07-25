package io.github.adamw7.tools.adopt.step;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;

import io.github.adamw7.tools.adopt.AdoptionException;
import io.github.adamw7.tools.adopt.command.CommandResult;
import io.github.adamw7.tools.adopt.command.RecordingCommandRunner;

class ToolProbeTest {

	private final ToolProbe probe = new ToolProbe();
	private final Path directory = Path.of("/tmp/workspace");

	@Test
	void probesEachToolWithAVersionFlagInTheGivenDirectory() {
		RecordingCommandRunner runner = new RecordingCommandRunner();
		probe.missingFrom(List.of("git", "gh"), directory, runner);
		assertEquals(List.of("git", "--version"), runner.commandAt(0));
		assertEquals(List.of("gh", "--version"), runner.commandAt(1));
		assertEquals(directory, runner.invocations().get(0).workingDirectory());
	}

	@Test
	void reportsNothingMissingWhenEveryProbeSucceeds() {
		assertEquals(List.of(), probe.missingFrom(List.of("git", "gh"), directory, new RecordingCommandRunner()));
	}

	/**
	 * Probing every tool even after one is found missing lets a single failure name
	 * all of the absent ones rather than sending the caller round the loop once per
	 * tool.
	 */
	@Test
	void reportsEveryMissingToolInDeclarationOrder() {
		RecordingCommandRunner runner = new RecordingCommandRunner(
				command -> command.contains("claude") ? new CommandResult(command, 0, "")
						: new CommandResult(command, 1, "missing"));
		assertEquals(List.of("git", "gh"), probe.missingFrom(List.of("git", "claude", "gh"), directory, runner));
		assertEquals(3, runner.count(), "every tool must be probed, not just up to the first missing one");
	}

	/**
	 * A tool the runner cannot even start throws out of run(); for a probe that is
	 * the "not installed" answer, not a reason to abort the whole check.
	 */
	@Test
	void aToolThatCannotStartCountsAsMissingRatherThanPropagating() {
		RecordingCommandRunner runner = new RecordingCommandRunner(command -> {
			throw new AdoptionException("Could not start command: " + String.join(" ", command));
		});
		assertEquals(List.of("git"), probe.missingFrom(List.of("git"), directory, runner));
	}

	@Test
	void anEmptyToolListProbesNothing() {
		RecordingCommandRunner runner = new RecordingCommandRunner();
		assertEquals(List.of(), probe.missingFrom(List.of(), directory, runner));
		assertEquals(0, runner.count());
	}

	@Test
	void succeedsRunsTheGivenCommandVerbatim() {
		RecordingCommandRunner runner = new RecordingCommandRunner();
		assertTrue(probe.succeeds(List.of("gh", "auth", "status"), directory, runner));
		assertEquals(List.of("gh", "auth", "status"), runner.commandAt(0));
	}

	@Test
	void succeedsIsFalseForANonZeroExit() {
		RecordingCommandRunner runner = new RecordingCommandRunner(
				command -> new CommandResult(command, 1, "not logged in"));
		assertFalse(probe.succeeds(List.of("gh", "auth", "status"), directory, runner));
	}

	@Test
	void succeedsIsFalseForACommandThatCannotStart() {
		RecordingCommandRunner runner = new RecordingCommandRunner(command -> {
			throw new AdoptionException("Could not start command");
		});
		assertFalse(probe.succeeds(List.of("gh", "auth", "status"), directory, runner));
	}
}
