package io.github.adamw7.tools.adopt.step;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;

import io.github.adamw7.tools.adopt.AdoptionContext;
import io.github.adamw7.tools.adopt.AdoptionException;
import io.github.adamw7.tools.adopt.command.CommandResult;
import io.github.adamw7.tools.adopt.command.RecordingCommandRunner;

class ToolchainStepTest {

	private final AdoptionContext context = new AdoptionContext("https://github.com/adamw7/demo.git",
			Path.of("/tmp/workspace"));

	@Test
	void probesEveryRequiredToolInTheWorkspace() {
		RecordingCommandRunner runner = new RecordingCommandRunner();
		new ToolchainStep().execute(context, runner);
		assertEquals(List.of("git", "--version"), runner.commandAt(0));
		assertEquals(List.of("claude", "--version"), runner.commandAt(1));
		assertEquals(List.of("gh", "--version"), runner.commandAt(2));
		assertEquals(context.workspace(), runner.invocations().get(0).workingDirectory());
	}

	@Test
	void probesTheConfiguredTools() {
		RecordingCommandRunner runner = new RecordingCommandRunner();
		new ToolchainStep(List.of("git")).execute(context, runner);
		assertEquals(1, runner.count());
		assertEquals(List.of("git", "--version"), runner.commandAt(0));
	}

	@Test
	void aToolThatExitsNonZeroAbortsTheAdoption() {
		RecordingCommandRunner runner = new RecordingCommandRunner(
				command -> command.contains("gh") ? new CommandResult(command, 127, "gh: not found")
						: new CommandResult(command, 0, ""));
		AdoptionException thrown = assertThrows(AdoptionException.class,
				() -> new ToolchainStep().execute(context, runner));
		assertTrue(thrown.getMessage().contains("gh"), thrown.getMessage());
	}

	@Test
	void aToolThatCannotStartAbortsTheAdoption() {
		RecordingCommandRunner runner = new RecordingCommandRunner(command -> {
			throw new AdoptionException("Could not start command: " + String.join(" ", command));
		});
		assertThrows(AdoptionException.class, () -> new ToolchainStep().execute(context, runner));
	}

	@Test
	void reportsEveryMissingToolAtOnce() {
		RecordingCommandRunner runner = new RecordingCommandRunner(
				command -> command.contains("git") ? new CommandResult(command, 0, "")
						: new CommandResult(command, 1, "missing"));
		AdoptionException thrown = assertThrows(AdoptionException.class,
				() -> new ToolchainStep().execute(context, runner));
		assertTrue(thrown.getMessage().contains("claude"), thrown.getMessage());
		assertTrue(thrown.getMessage().contains("gh"), thrown.getMessage());
	}

	@Test
	void isNamedToolchain() {
		assertEquals("toolchain", new ToolchainStep().name());
	}
}
