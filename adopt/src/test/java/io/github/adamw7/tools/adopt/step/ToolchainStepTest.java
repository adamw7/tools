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

	@Test
	void anAuthenticatedGitHubCliCompletesTheStep() {
		RecordingCommandRunner runner = new RecordingCommandRunner();
		assertDoesNotThrow(() -> new ToolchainStep().execute(context, runner));
		assertEquals(4, runner.count());
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

	/**
	 * The failure the probe was changed for: a {@code gh} holding a token GitHub
	 * rejects prints that the token is invalid and still exits <em>zero</em> from
	 * {@code gh auth status}. A probe that read that stored credential state passed
	 * for a CLI which could not open a pull request, and an adoption ran a clone, a
	 * {@code claude init}, a build and a push before failing at {@code pull-request}
	 * — the very outcome this step exists to prevent.
	 */
	@Test
	void aGitHubCliWhoseTokenGitHubRejectsAbortsTheAdoption() {
		RecordingCommandRunner runner = new RecordingCommandRunner(this::rejectedByGitHub);
		AdoptionException thrown = assertThrows(AdoptionException.class,
				() -> new ToolchainStep().execute(context, runner));
		assertTrue(thrown.getMessage().contains("cannot authenticate"), thrown.getMessage());
	}

	/**
	 * Pins the probe away from {@code gh auth status} by behaviour rather than by
	 * name, so a change back to any command that only reports what {@code gh} has
	 * stored fails here instead of at an adoption's last step.
	 */
	@Test
	void neverAsksGhWhatCredentialsItHolds() {
		RecordingCommandRunner runner = new RecordingCommandRunner();
		new ToolchainStep().execute(context, runner);
		assertFalse(runner.invocations().stream().anyMatch(invocation -> invocation.command().contains("auth")),
				runner.invocations().toString());
	}

	/** A {@code gh} that is installed and holds a token GitHub answers 401 to. */
	private CommandResult rejectedByGitHub(List<String> command) {
		if (command.contains("auth")) {
			return new CommandResult(command, 0, "X Failed to log in to github.com using token (GH_TOKEN)");
		}
		return command.contains("api") ? new CommandResult(command, 1, "HTTP 401: Bad credentials")
				: new CommandResult(command, 0, "");
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
