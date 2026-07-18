package io.github.adamw7.tools.adopt.step;

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
		RecordingCommandRunner runner = new RecordingCommandRunner(this::noExistingPullRequest);
		step.execute(context, runner);
		assertEquals(List.of("gh", "pr", "view", "claude/adopt-claude-code", "--json", "url"),
				runner.commandAt(0));
		assertEquals(List.of("gh", "pr", "create", "--title", PullRequestOptions.DEFAULT_TITLE, "--body",
				PullRequestOptions.DEFAULT_BODY, "--head", "claude/adopt-claude-code"), runner.commandAt(1));
		assertEquals(context.repositoryDirectory(), runner.invocations().get(1).workingDirectory());
	}

	@Test
	void usesConfiguredTitleAndBody() {
		RecordingCommandRunner runner = new RecordingCommandRunner(this::noExistingPullRequest);
		new PullRequestStep("My title", "My body").execute(context, runner);
		assertEquals(List.of("gh", "pr", "create", "--title", "My title", "--body", "My body", "--head",
				"claude/adopt-claude-code"), runner.commandAt(1));
	}

	@Test
	void requestsReviewersLabelsAssigneesAndOpensAsDraft() {
		RecordingCommandRunner runner = new RecordingCommandRunner(this::noExistingPullRequest);
		PullRequestOptions options = PullRequestOptions.builder()
				.title("My title")
				.body("My body")
				.reviewers(List.of("octocat", "hubot"))
				.labels(List.of("automation"))
				.assignees(List.of("adamw7"))
				.draft(true)
				.build();
		new PullRequestStep(options).execute(context, runner);
		assertEquals(List.of("gh", "pr", "create", "--title", "My title", "--body", "My body", "--head",
				"claude/adopt-claude-code", "--draft", "--reviewer", "octocat", "--reviewer", "hubot", "--label",
				"automation", "--assignee", "adamw7"), runner.commandAt(1));
	}

	@Test
	void skipsCreationWhenAPullRequestAlreadyExists() {
		RecordingCommandRunner runner = new RecordingCommandRunner();
		step.execute(context, runner);
		assertEquals(1, runner.count());
		assertEquals(List.of("gh", "pr", "view", "claude/adopt-claude-code", "--json", "url"),
				runner.commandAt(0));
	}

	@Test
	void failedCreationAbortsAdoption() {
		RecordingCommandRunner runner = new RecordingCommandRunner(this::createFails);
		assertThrows(AdoptionException.class, () -> step.execute(context, runner));
	}

	private CommandResult noExistingPullRequest(List<String> command) {
		if (command.contains("view")) {
			return new CommandResult(command, 1, "no pull requests found");
		}
		return new CommandResult(command, 0, "https://github.com/adamw7/tools/pull/1");
	}

	private CommandResult createFails(List<String> command) {
		if (command.contains("view")) {
			return new CommandResult(command, 1, "no pull requests found");
		}
		return new CommandResult(command, 1, "gh: could not authenticate");
	}

	@Test
	void isNamedPullRequest() {
		assertEquals("pull-request", step.name());
	}
}
