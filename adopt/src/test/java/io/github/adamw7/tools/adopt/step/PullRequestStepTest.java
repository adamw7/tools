package io.github.adamw7.tools.adopt.step;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;

import io.github.adamw7.tools.adopt.AdoptionContext;
import io.github.adamw7.tools.adopt.AdoptionException;
import io.github.adamw7.tools.adopt.command.CommandResult;
import io.github.adamw7.tools.adopt.command.RecordingCommandRunner;

class PullRequestStepTest {

	private final AdoptionContext context = new AdoptionContext("https://github.com/adamw7/tools.git",
			Path.of("/tmp/workspace"), "claude/adopt-claude-code");
	private final PullRequestStep step = new PullRequestStep();

	@Test
	void opensPullRequestForFeatureBranch() {
		RecordingCommandRunner runner = new RecordingCommandRunner();
		step.execute(context, runner);
		assertEquals(List.of("gh", "pr", "create", "--title", PullRequestStep.DEFAULT_TITLE, "--body",
				PullRequestStep.DEFAULT_BODY, "--head", "claude/adopt-claude-code"), runner.commandAt(0));
		assertEquals(context.repositoryDirectory(), runner.invocations().get(0).workingDirectory());
	}

	@Test
	void usesConfiguredTitleAndBody() {
		RecordingCommandRunner runner = new RecordingCommandRunner();
		new PullRequestStep("My title", "My body").execute(context, runner);
		assertEquals(List.of("gh", "pr", "create", "--title", "My title", "--body", "My body", "--head",
				"claude/adopt-claude-code"), runner.commandAt(0));
	}

	@Test
	void toleratesAnAlreadyOpenPullRequest() {
		RecordingCommandRunner runner = new RecordingCommandRunner(command -> new CommandResult(command, 1,
				"a pull request for branch \"claude/adopt-claude-code\" already exists"));
		assertDoesNotThrow(() -> step.execute(context, runner));
	}

	@Test
	void toleratesNoCommitsBetweenBaseAndHead() {
		RecordingCommandRunner runner = new RecordingCommandRunner(
				command -> new CommandResult(command, 1, "No commits between main and claude/adopt-claude-code"));
		assertDoesNotThrow(() -> step.execute(context, runner));
	}

	@Test
	void otherFailureAbortsAdoption() {
		RecordingCommandRunner runner = new RecordingCommandRunner(
				command -> new CommandResult(command, 1, "gh: could not authenticate"));
		assertThrows(AdoptionException.class, () -> step.execute(context, runner));
	}

	@Test
	void isNamedPullRequest() {
		assertEquals("pull-request", step.name());
	}
}
