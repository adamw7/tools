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
		assertEquals(List.of("gh", "api", "repos/adamw7/demo"), runner.commandAt(3));
		assertEquals(context.workspace(), runner.invocations().get(3).workingDirectory());
	}

	/**
	 * The probe must not be the user-scoped {@code gh api user}. A GitHub App
	 * installation token — what a CI run is handed, and the credential behind the
	 * {@code x-access-token:TOKEN@github.com} clone URLs the adoption accepts — is
	 * refused by that endpoint with "Resource not accessible by integration", so
	 * probing with it aborted every CI-driven adoption on its very first step even
	 * though {@code gh pr create} would have worked.
	 */
	@Test
	void neverProbesTheTokensOwnerWhenTheUrlNamesTheRepository() {
		RecordingCommandRunner runner = new RecordingCommandRunner();
		new ToolchainStep().execute(context, runner);
		assertFalse(runner.invocations().stream().anyMatch(invocation -> invocation.command().contains("user")),
				runner.invocations().toString());
	}

	/** An installation token reads its own repository, which is the access the pull request needs. */
	@Test
	void anInstallationTokenThatCanReadTheRepositoryCompletesTheStep() {
		RecordingCommandRunner runner = new RecordingCommandRunner(ToolchainStepTest::installationToken);
		assertDoesNotThrow(() -> new ToolchainStep().execute(context, runner));
	}

	/**
	 * The stronger, repository-scoped question is asked of a self-hosted GitHub on its
	 * own port too. Reading the port as a path segment left the URL apparently
	 * ownerless, so the step fell back to {@code gh api user} — the user-scoped call a
	 * GitHub App installation token is refused by, and the very credential the ported
	 * enterprise host is most likely to be driven with.
	 */
	@Test
	void asksAboutTheRepositoryOfAHostReachedOnAPort() {
		AdoptionContext ported = new AdoptionContext("https://ghe.example.com:8443/adamw7/tools.git",
				Path.of("/tmp/workspace"));
		RecordingCommandRunner runner = new RecordingCommandRunner();

		new ToolchainStep().execute(ported, runner);

		assertEquals(List.of("gh", "api", "repos/adamw7/tools"), runner.commandAt(3));
	}

	/** A token GitHub answers, but not for this repository, cannot open the pull request either. */
	@Test
	void aTokenThatCannotSeeTheRepositoryAbortsTheAdoption() {
		RecordingCommandRunner runner = RecordingCommandRunner.failingOn("repos/adamw7/demo", 1, "HTTP 404");
		AdoptionException thrown = assertThrows(AdoptionException.class,
				() -> new ToolchainStep().execute(context, runner));
		assertTrue(thrown.getMessage().contains("adamw7/demo"), thrown.getMessage());
	}

	/**
	 * A URL naming no owner — a local path — leaves no repository to ask about, so
	 * the weaker question is all there is.
	 */
	@Test
	void asksWhoTheCredentialsBelongToWhenTheUrlNamesNoRepository() {
		AdoptionContext local = new AdoptionContext("/srv/git/demo", Path.of("/tmp/workspace"));
		RecordingCommandRunner runner = new RecordingCommandRunner();
		new ToolchainStep().execute(local, runner);
		assertEquals(List.of("gh", "api", "user"), runner.commandAt(3));
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
		RecordingCommandRunner runner = RecordingCommandRunner.failingOn("api", 1, "HTTP 401");
		AdoptionException thrown = assertThrows(AdoptionException.class,
				() -> new ToolchainStep().execute(context, runner));
		assertTrue(thrown.getMessage().contains("cannot reach"), thrown.getMessage());
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
		assertTrue(thrown.getMessage().contains("cannot reach"), thrown.getMessage());
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

	/** A {@code gh} holding a GitHub App installation token: refused by {@code user}, fine on the repository. */
	private static CommandResult installationToken(List<String> command) {
		if (command.contains("user")) {
			return new CommandResult(command, 1, "HTTP 403: Resource not accessible by integration");
		}
		return new CommandResult(command, 0, "");
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
		RecordingCommandRunner runner = RecordingCommandRunner.failing(1, "missing");
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
		RecordingCommandRunner runner = RecordingCommandRunner.failingOn("gh", 127, "gh: not found");
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
		RecordingCommandRunner runner = RecordingCommandRunner.failing(1, "missing");
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
