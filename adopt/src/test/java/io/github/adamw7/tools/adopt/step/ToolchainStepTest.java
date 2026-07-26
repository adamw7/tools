package io.github.adamw7.tools.adopt.step;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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
	void probesTheGitHubLoginOnceEveryToolIsPresent() {
		RecordingCommandRunner runner = new RecordingCommandRunner();
		new ToolchainStep().execute(context, runner);
		assertEquals(List.of("gh", "api", "user"), runner.commandAt(3));
		assertEquals(context.workspace(), runner.invocations().get(3).workingDirectory());
	}

	/**
	 * The probe must call GitHub rather than read what {@code gh} has stored locally:
	 * {@code gh auth status} prints that a rejected {@code GH_TOKEN} is invalid and
	 * still exits zero, which passed the check for a CLI that could not open a pull
	 * request at all.
	 */
	@Test
	void probesTheGitHubLoginWithAnAuthenticatedCallRatherThanTheLocalCredentialState() {
		RecordingCommandRunner runner = new RecordingCommandRunner();
		new ToolchainStep().execute(context, runner);
		assertFalse(runner.commandAt(3).contains("auth"), runner.commandAt(3).toString());
	}

	/**
	 * {@code gh --version} succeeds for a GitHub CLI nobody is logged in to, so
	 * without this the adoption would only find out at its very last step, after a
	 * clone, a claude init, and a build have already run.
	 */
	@Test
	void anUnauthenticatedGitHubCliAbortsTheAdoption() {
		RecordingCommandRunner runner = new RecordingCommandRunner(
				command -> command.contains("api") ? new CommandResult(command, 1, "HTTP 401")
						: new CommandResult(command, 0, ""));
		AdoptionException thrown = assertThrows(AdoptionException.class,
				() -> new ToolchainStep().execute(context, runner));
		assertTrue(thrown.getMessage().contains("cannot authenticate"), thrown.getMessage());
	}

	@Test
	void doesNotProbeTheGitHubLoginWhenGhIsNotARequiredTool() {
		RecordingCommandRunner runner = new RecordingCommandRunner();
		new ToolchainStep(List.of("git")).execute(context, runner);
		assertEquals(1, runner.count());
	}

	@Test
	void reportsAMissingToolWithoutProbingTheGitHubLogin() {
		RecordingCommandRunner runner = new RecordingCommandRunner(
				command -> new CommandResult(command, 1, "missing"));
		assertThrows(AdoptionException.class, () -> new ToolchainStep().execute(context, runner));
		assertEquals(3, runner.count());
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
	void reportsMissingToolsInDeclarationOrder() {
		RecordingCommandRunner runner = new RecordingCommandRunner(
				command -> new CommandResult(command, 1, "missing"));
		AdoptionException thrown = assertThrows(AdoptionException.class,
				() -> new ToolchainStep(List.of("git", "gh")).execute(context, runner));
		assertTrue(thrown.getMessage().contains("git, gh"), thrown.getMessage());
	}

	@Test
	void anEmptyToolListIsANoOpThatPasses() {
		RecordingCommandRunner runner = new RecordingCommandRunner();
		assertDoesNotThrow(() -> new ToolchainStep(List.of()).execute(context, runner));
		assertEquals(0, runner.count());
	}

	@Test
	void isNamedToolchain() {
		assertEquals("toolchain", new ToolchainStep().name());
	}
}
