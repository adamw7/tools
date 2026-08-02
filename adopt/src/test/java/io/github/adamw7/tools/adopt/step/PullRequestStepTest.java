package io.github.adamw7.tools.adopt.step;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

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

	/**
	 * gh's diagnostics are merged into the captured output, and one printed before
	 * the JSON can itself contain a '['. Stopping at the first bracket would parse
	 * the noise, conclude no pull request is open, and create a duplicate that gh
	 * rejects — failing an adoption that was only being re-run.
	 */
	@Test
	void findsTheOpenPullRequestDespiteLeadingBracketedNoise() {
		RecordingCommandRunner runner = new RecordingCommandRunner(command -> command.contains("list")
				? new CommandResult(command, 0, "warning: [core] ignoring stale entry\n"
						+ "[{\"url\":\"https://github.com/adamw7/demo/pull/7\"}]\n")
				: new CommandResult(command, 0, ""));
		AdoptionReport report = new AdoptionReport();
		new PullRequestStep().execute(context, runner, report);
		assertEquals(Optional.of("https://github.com/adamw7/demo/pull/7"), report.pullRequestUrl());
		assertFalse(runner.invocations().stream().anyMatch(invocation -> invocation.command().contains("create")),
				"an already-open pull request must not be created a second time");
	}

	@Test
	void anEmptyArrayAfterNoiseStillMeansNoOpenPullRequest() {
		RecordingCommandRunner runner = new RecordingCommandRunner(command -> command.contains("list")
				? new CommandResult(command, 0, "warning: [core] ignoring stale entry\n[]\n")
				: new CommandResult(command, 0, ""));
		new PullRequestStep().execute(context, runner, new AdoptionReport());
		assertTrue(runner.invocations().stream().anyMatch(invocation -> invocation.command().contains("create")));
	}

	@Test
	void opensPullRequestForFeatureBranch() {
		RecordingCommandRunner runner = new RecordingCommandRunner(this::noOpenPullRequest);
		step.execute(context, runner);
		assertEquals(List.of("gh", "pr", "list", "--head", "claude/adopt-claude-code", "--state", "open", "--json",
				"url", "--repo", "adamw7/tools"), runner.commandAt(0));
		assertEquals(List.of("gh", "pr", "create", "--title", PullRequestOptions.DEFAULT_TITLE, "--body",
				PullRequestOptions.DEFAULT_BODY, "--head", "claude/adopt-claude-code", "--repo", "adamw7/tools"),
				runner.commandAt(1));
		assertEquals(context.repositoryDirectory(), runner.invocations().get(1).workingDirectory());
	}

	/**
	 * gh infers the repository from the checkout's git remote, which is not always
	 * readable as a GitHub one — a {@code url.<base>.insteadOf} rewrite, a mirror,
	 * or a proxied clone all leave it reporting that no remote points at a known
	 * GitHub host, failing the last step of an otherwise complete adoption. The
	 * adoption already knows the repository, so it says so.
	 */
	@Test
	void namesTheTargetRepositoryRatherThanLettingGhInferItFromTheRemote() {
		RecordingCommandRunner runner = new RecordingCommandRunner(this::noOpenPullRequest);
		AdoptionContext proxied = new AdoptionContext("git@github.com:adamw7/tools.git", Path.of("/tmp/workspace"));
		new PullRequestStep().execute(proxied, runner);
		assertTrue(runner.commandAt(0).containsAll(List.of("--repo", "adamw7/tools")));
		assertTrue(runner.commandAt(1).containsAll(List.of("--repo", "adamw7/tools")));
	}

	@Test
	void omitsTheRepositoryFlagWhenTheUrlNamesNoOwner() {
		RecordingCommandRunner runner = new RecordingCommandRunner(this::noOpenPullRequest);
		AdoptionContext local = new AdoptionContext("/srv/mirrors/tools", Path.of("/tmp/workspace"));
		new PullRequestStep().execute(local, runner);
		assertFalse(runner.commandAt(0).contains("--repo"), "gh must fall back to its own inference");
		assertFalse(runner.commandAt(1).contains("--repo"));
	}

	/**
	 * A self-hosted GitHub reached on its own port names an owner like any other host.
	 * Reading the port as a path segment left the URL apparently ownerless, so the
	 * flag was dropped and {@code gh} was left to infer the repository from a remote
	 * that an {@code insteadOf} rewrite or a proxied clone can make unreadable —
	 * failing the last step of an otherwise complete adoption.
	 */
	@Test
	void namesTheRepositoryOfAHostReachedOnAPort() {
		RecordingCommandRunner runner = new RecordingCommandRunner(this::noOpenPullRequest);
		AdoptionContext ported = new AdoptionContext("https://ghe.example.com:8443/adamw7/tools.git",
				Path.of("/tmp/workspace"));

		new PullRequestStep().execute(ported, runner);

		assertTrue(runner.commandAt(0).containsAll(List.of("--repo", "adamw7/tools")), runner.commandAt(0).toString());
		assertTrue(runner.commandAt(1).containsAll(List.of("--repo", "adamw7/tools")), runner.commandAt(1).toString());
	}

	@Test
	void usesConfiguredTitleAndBody() {
		RecordingCommandRunner runner = new RecordingCommandRunner(this::noOpenPullRequest);
		new PullRequestStep(new PullRequestOptions("My title", "My body", List.of(), List.of(), List.of(), false))
				.execute(context, runner);
		assertEquals(List.of("gh", "pr", "create", "--title", "My title", "--body", "My body", "--head",
				"claude/adopt-claude-code", "--repo", "adamw7/tools"), runner.commandAt(1));
	}

	@Test
	void requestsReviewersLabelsAssigneesAndOpensAsDraft() {
		RecordingCommandRunner runner = new RecordingCommandRunner(this::noOpenPullRequest);
		PullRequestOptions options = new PullRequestOptions("My title", "My body", List.of("octocat", "hubot"),
				List.of("automation"), List.of("adamw7"), true);
		new PullRequestStep(options).execute(context, runner);
		assertEquals(List.of("gh", "pr", "create", "--title", "My title", "--body", "My body", "--head",
				"claude/adopt-claude-code", "--repo", "adamw7/tools", "--draft", "--reviewer", "octocat", "--reviewer",
				"hubot", "--label", "automation", "--assignee", "adamw7"), runner.commandAt(1));
	}

	@Test
	void skipsCreationWhenAnOpenPullRequestAlreadyExists() {
		RecordingCommandRunner runner = new RecordingCommandRunner(this::existingPullRequest);
		step.execute(context, runner);
		assertEquals(1, runner.count());
		assertEquals(List.of("gh", "pr", "list", "--head", "claude/adopt-claude-code", "--state", "open", "--json",
				"url", "--repo", "adamw7/tools"), runner.commandAt(0));
	}

	@Test
	void createsAFreshPullRequestWhenOnlyAClosedOneExists() {
		RecordingCommandRunner runner = new RecordingCommandRunner(this::noOpenPullRequest);
		step.execute(context, runner);
		assertEquals(List.of("gh", "pr", "create", "--title", PullRequestOptions.DEFAULT_TITLE, "--body",
				PullRequestOptions.DEFAULT_BODY, "--head", "claude/adopt-claude-code", "--repo", "adamw7/tools"),
				runner.commandAt(1));
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
		RecordingCommandRunner runner = RecordingCommandRunner.answering("not json at all");
		AdoptionReport report = new AdoptionReport();
		step.execute(context, runner, report);
		assertTrue(report.pullRequestUrl().isEmpty());
	}

	@Test
	void leavesUrlAbsentWhenUrlFieldIsMissing() {
		RecordingCommandRunner runner = RecordingCommandRunner.answering("[{\"number\": 1}]");
		AdoptionReport report = new AdoptionReport();
		step.execute(context, runner, report);
		assertTrue(report.pullRequestUrl().isEmpty());
	}

	/**
	 * A list that cannot even be run — an unauthenticated {@code gh}, a remote that
	 * is not a GitHub repository — must not be read as "a pull request is already
	 * open", or the adoption would report success having created nothing.
	 */
	@Test
	void createsThePullRequestWhenTheListQueryItselfFails() {
		RecordingCommandRunner runner = new RecordingCommandRunner(this::listFails);
		step.execute(context, runner);
		assertTrue(runner.invocations().stream().anyMatch(invocation -> invocation.command().contains("create")),
				"creation must still be attempted when the pre-check could not be answered");
	}

	/** Output cut short mid-document — a killed {@code gh}, a truncated pipe. */
	@Test
	void leavesUrlAbsentWhenListOutputIsTruncatedJson() {
		RecordingCommandRunner runner = RecordingCommandRunner.answering("[{\"url\": ");
		AdoptionReport report = new AdoptionReport();
		step.execute(context, runner, report);
		assertTrue(report.pullRequestUrl().isEmpty());
	}

	@Test
	void failedCreationAbortsAdoption() {
		RecordingCommandRunner runner = new RecordingCommandRunner(this::createFails);
		assertThrows(AdoptionException.class, () -> step.execute(context, runner));
	}

	/**
	 * Re-adopting a repository that is already adopted leaves every step with its
	 * work done and so the branch with no commits the base does not already have.
	 * There is nothing to raise a pull request for, which is the run succeeding —
	 * aborting the last step of a pipeline that found nothing left to do would make
	 * the adoption impossible to re-run.
	 */
	@Test
	void aBranchWithNoCommitsToProposeIsNotAFailure() {
		RecordingCommandRunner runner = new RecordingCommandRunner(command -> command.contains("create")
				? new CommandResult(command, 1, "pull request create failed: GraphQL: No commits between main and "
						+ "claude/adopt-claude-code (createPullRequest)")
				: new CommandResult(command, 0, "[]"));
		step.execute(context, runner);
		assertTrue(runner.invocations().stream().anyMatch(invocation -> invocation.command().contains("create")),
				"the creation must have been attempted");
	}

	/**
	 * The pre-check that would have caught this cannot always run: {@code gh pr
	 * list} needs a query a restricted token or a proxied host may refuse, and its
	 * failure is indistinguishable from "no pull request is open". The create that
	 * follows then reports the pull request already exists, which is the same no-op
	 * the pre-check would have made it.
	 */
	@Test
	void anAlreadyOpenPullRequestIsNotAFailureEvenWhenTheQueryCouldNotRunFirst() {
		RecordingCommandRunner runner = new RecordingCommandRunner(command -> command.contains("create")
				? new CommandResult(command, 1, "a pull request for branch \"claude/adopt-claude-code\" into branch"
						+ " \"main\" already exists:\n" + PR_URL)
				: new CommandResult(command, 1, "HTTP 403: this GraphQL query is not enabled for this session"));
		step.execute(context, runner);
		assertTrue(runner.invocations().stream().anyMatch(invocation -> invocation.command().contains("create")));
	}

	/** The other wording gh reports it with, when its own pre-check missed the pull request. */
	@Test
	void anAlreadyOpenPullRequestReportedByGraphQlIsNotAFailure() {
		RecordingCommandRunner runner = new RecordingCommandRunner(command -> command.contains("create")
				? new CommandResult(command, 1, "pull request create failed: GraphQL: A pull request already exists"
						+ " for adamw7:claude/adopt-claude-code.")
				: new CommandResult(command, 0, "[]"));
		step.execute(context, runner);
		assertTrue(runner.invocations().stream().anyMatch(invocation -> invocation.command().contains("create")));
	}

	/**
	 * Tolerating the creation is only half of it: the pull request gh refused to
	 * open a second time is the one the report must carry, which the read-back that
	 * follows is what supplies.
	 */
	@Test
	void recordsTheUrlOfThePullRequestThatAlreadyExisted() {
		AtomicInteger listCalls = new AtomicInteger();
		RecordingCommandRunner runner = new RecordingCommandRunner(command -> {
			if (command.contains("create")) {
				return new CommandResult(command, 1, "a pull request for branch \"claude/adopt-claude-code\""
						+ " into branch \"main\" already exists:\n" + PR_URL);
			}
			// The pre-check misses it — a restricted token, a proxied host — and the
			// read-back after the tolerated creation is what finds it.
			return new CommandResult(command, 0,
					listCalls.getAndIncrement() == 0 ? "[]" : "[{\"url\":\"" + PR_URL + "\"}]");
		});
		AdoptionReport report = new AdoptionReport();
		step.execute(context, runner, report);
		assertEquals(PR_URL, report.pullRequestUrl().orElseThrow());
	}

	/**
	 * The tolerated wordings are matched against gh's whole merged transcript, so a
	 * fragment that does not name a pull request matches a failure that has nothing
	 * to do with one. A bare "already exists" swallowed these, and the adoption was
	 * reported as complete with no pull request opened and none to find.
	 */
	@Test
	void aFailureMentioningSomethingElseThatAlreadyExistsStillAbortsAdoption() {
		RecordingCommandRunner runner = new RecordingCommandRunner(command -> command.contains("create")
				? new CommandResult(command, 1, "could not add label: 'infra' already exists on another repository")
				: new CommandResult(command, 0, "[]"));
		assertThrows(AdoptionException.class, () -> step.execute(context, runner));
	}

	@Test
	void aRefThatAlreadyExistsStillAbortsAdoption() {
		RecordingCommandRunner runner = new RecordingCommandRunner(command -> command.contains("create")
				? new CommandResult(command, 1, "pull request create failed: GraphQL: A ref named"
						+ " \"refs/heads/claude/adopt-claude-code\" already exists in the repository")
				: new CommandResult(command, 0, "[]"));
		assertThrows(AdoptionException.class, () -> step.execute(context, runner));
	}

	@Test
	void aCreationFailureThatIsNotBenignStillAbortsAdoption() {
		RecordingCommandRunner runner = new RecordingCommandRunner(command -> command.contains("create")
				? new CommandResult(command, 1, "gh: Resource not accessible by integration")
				: new CommandResult(command, 0, "[]"));
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

	private CommandResult listFails(List<String> command) {
		if (command.contains("list")) {
			return new CommandResult(command, 1, "gh: not authenticated");
		}
		return new CommandResult(command, 0, PR_URL);
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
