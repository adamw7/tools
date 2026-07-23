package io.github.adamw7.tools.adopt.step;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;

import io.github.adamw7.tools.adopt.AdoptionContext;
import io.github.adamw7.tools.adopt.AdoptionException;
import io.github.adamw7.tools.adopt.AdoptionReport;
import io.github.adamw7.tools.adopt.command.CommandResult;
import io.github.adamw7.tools.adopt.command.RecordingCommandRunner;

class PullRequestStepTest {

	private static final String PR_URL = "https://github.com/adamw7/tools/pull/1";

	private final AdoptionContext context = new AdoptionContext("https://github.com/adamw7/tools.git",
			Path.of("/tmp/workspace"), "claude/adopt-claude-code");
	private final PullRequestStep step = new PullRequestStep();

	@Test
	void opensPullRequestForFeatureBranch() {
		RecordingCommandRunner runner = new RecordingCommandRunner(this::noOpenPullRequest);
		step.execute(context, runner);
		assertEquals(List.of("gh", "pr", "list", "--head", "claude/adopt-claude-code", "--state", "open", "--json",
				"url"), runner.commandAt(0));
		assertEquals(List.of("gh", "pr", "create", "--title", PullRequestOptions.DEFAULT_TITLE, "--body",
				PullRequestOptions.DEFAULT_BODY, "--head", "claude/adopt-claude-code"), runner.commandAt(1));
		assertEquals(context.repositoryDirectory(), runner.invocations().get(1).workingDirectory());
	}

	@Test
	void usesConfiguredTitleAndBody() {
		RecordingCommandRunner runner = new RecordingCommandRunner(this::noOpenPullRequest);
		new PullRequestStep("My title", "My body").execute(context, runner);
		assertEquals(List.of("gh", "pr", "create", "--title", "My title", "--body", "My body", "--head",
				"claude/adopt-claude-code"), runner.commandAt(1));
	}

	@Test
	void requestsReviewersLabelsAssigneesAndOpensAsDraft() {
		RecordingCommandRunner runner = new RecordingCommandRunner(this::noOpenPullRequest);
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
	void skipsCreationWhenAnOpenPullRequestAlreadyExists() {
		RecordingCommandRunner runner = new RecordingCommandRunner(this::existingPullRequest);
		step.execute(context, runner);
		assertEquals(1, runner.count());
		assertEquals(List.of("gh", "pr", "list", "--head", "claude/adopt-claude-code", "--state", "open", "--json",
				"url"), runner.commandAt(0));
	}

	@Test
	void createsAFreshPullRequestWhenOnlyAClosedOneExists() {
		RecordingCommandRunner runner = new RecordingCommandRunner(this::noOpenPullRequest);
		step.execute(context, runner);
		assertEquals(List.of("gh", "pr", "create", "--title", PullRequestOptions.DEFAULT_TITLE, "--body",
				PullRequestOptions.DEFAULT_BODY, "--head", "claude/adopt-claude-code"), runner.commandAt(1));
	}

	@Test
	void recordsCreatedPullRequestUrlInReport() {
		RecordingCommandRunner runner = new RecordingCommandRunner(new ViewFailsUntilCreated());
		AdoptionReport report = new AdoptionReport();
		step.execute(context, runner, report);
		assertEquals(PR_URL, report.pullRequestUrl().orElseThrow());
	}

	@Test
	void recordsExistingPullRequestUrlInReport() {
		RecordingCommandRunner runner = new RecordingCommandRunner(this::existingPullRequest);
		AdoptionReport report = new AdoptionReport();
		step.execute(context, runner, report);
		assertEquals(PR_URL, report.pullRequestUrl().orElseThrow());
	}

	@Test
	void recordsUrlEvenWhenListOutputCarriesNoise() {
		RecordingCommandRunner runner = new RecordingCommandRunner(
				command -> new CommandResult(command, 0,
						"a new gh release is available\n[{\"url\":\"" + PR_URL + "\"}]"));
		AdoptionReport report = new AdoptionReport();
		step.execute(context, runner, report);
		assertEquals(PR_URL, report.pullRequestUrl().orElseThrow());
	}

	@Test
	void leavesUrlAbsentWhenListOutputIsNotJson() {
		RecordingCommandRunner runner = new RecordingCommandRunner(
				command -> new CommandResult(command, 0, "not json at all"));
		AdoptionReport report = new AdoptionReport();
		step.execute(context, runner, report);
		assertTrue(report.pullRequestUrl().isEmpty());
	}

	@Test
	void leavesUrlAbsentWhenUrlFieldIsMissing() {
		RecordingCommandRunner runner = new RecordingCommandRunner(
				command -> new CommandResult(command, 0, "[{\"number\": 1}]"));
		AdoptionReport report = new AdoptionReport();
		step.execute(context, runner, report);
		assertTrue(report.pullRequestUrl().isEmpty());
	}

	@Test
	void failedCreationAbortsAdoption() {
		RecordingCommandRunner runner = new RecordingCommandRunner(this::createFails);
		assertThrows(AdoptionException.class, () -> step.execute(context, runner));
	}

	private CommandResult noOpenPullRequest(List<String> command) {
		if (command.contains("list")) {
			return new CommandResult(command, 0, "[]");
		}
		return new CommandResult(command, 0, PR_URL);
	}

	private CommandResult existingPullRequest(List<String> command) {
		return new CommandResult(command, 0, "[{\"url\":\"" + PR_URL + "\"}]");
	}

	private CommandResult createFails(List<String> command) {
		if (command.contains("list")) {
			return new CommandResult(command, 0, "[]");
		}
		return new CommandResult(command, 1, "gh: could not authenticate");
	}

	/**
	 * Mimics the real gh behaviour around creation: the pre-check list finds no open
	 * pull request, and every list after the create returns the branch's open one.
	 */
	private static final class ViewFailsUntilCreated
			implements java.util.function.Function<List<String>, CommandResult> {

		private boolean created;

		@Override
		public CommandResult apply(List<String> command) {
			if (command.contains("create")) {
				created = true;
				return new CommandResult(command, 0, PR_URL);
			}
			if (created) {
				return new CommandResult(command, 0, "[{\"url\":\"" + PR_URL + "\"}]");
			}
			return new CommandResult(command, 0, "[]");
		}
	}

	@Test
	void isNamedPullRequest() {
		assertEquals("pull-request", step.name());
	}
}
