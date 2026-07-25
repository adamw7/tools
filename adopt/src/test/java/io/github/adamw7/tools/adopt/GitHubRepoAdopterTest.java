package io.github.adamw7.tools.adopt;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import io.github.adamw7.tools.adopt.command.CommandResult;
import io.github.adamw7.tools.adopt.command.CommandRunner;
import io.github.adamw7.tools.adopt.command.RecordingCommandRunner;
import io.github.adamw7.tools.adopt.step.AdoptionStep;
import io.github.adamw7.tools.adopt.step.PullRequestOptions;

class GitHubRepoAdopterTest {

	private final AdoptionContext context = new AdoptionContext("https://github.com/adamw7/tools.git",
			Path.of("/tmp/workspace"));

	private static final class NamingStep implements AdoptionStep {

		private final String name;
		private final List<String> log;

		NamingStep(String name, List<String> log) {
			this.name = name;
			this.log = log;
		}

		@Override
		public String name() {
			return name;
		}

		@Override
		public void execute(AdoptionContext context, CommandRunner runner) {
			log.add(name);
		}
	}

	private static final class ExplodingStep implements AdoptionStep {

		@Override
		public String name() {
			return "boom";
		}

		@Override
		public void execute(AdoptionContext context, CommandRunner runner) {
			throw new AdoptionException("boom");
		}
	}

	private static final class SilentlyExplodingStep implements AdoptionStep {

		@Override
		public String name() {
			return "quiet";
		}

		@Override
		public void execute(AdoptionContext context, CommandRunner runner) {
			throw new IllegalStateException();
		}
	}

	@Test
	void runsEveryStepInOrder() {
		List<String> order = new ArrayList<>();
		List<AdoptionStep> steps = List.of(new NamingStep("a", order), new NamingStep("b", order));
		new GitHubRepoAdopter(new RecordingCommandRunner(), steps).adopt(context);
		assertEquals(List.of("a", "b"), order);
	}

	@Test
	void reportsEveryCompletedStep() {
		List<String> order = new ArrayList<>();
		List<AdoptionStep> steps = List.of(new NamingStep("a", order), new NamingStep("b", order));
		AdoptionReport report = new GitHubRepoAdopter(new RecordingCommandRunner(), steps).adopt(context);
		assertEquals(List.of("a", "b"), report.completedSteps());
	}

	@Test
	void stopsAtFirstFailingStep() {
		List<String> order = new ArrayList<>();
		List<AdoptionStep> steps = List.of(new NamingStep("a", order), new ExplodingStep(),
				new NamingStep("c", order));
		GitHubRepoAdopter adopter = new GitHubRepoAdopter(new RecordingCommandRunner(), steps);
		assertThrows(AdoptionException.class, () -> adopter.adopt(context));
		assertEquals(List.of("a"), order);
	}

	/**
	 * A report the adopter creates itself is lost the moment a step throws, which is
	 * when a caller most wants to know how far the run got. Supplying the report
	 * keeps it readable after the failure has propagated.
	 */
	@Test
	void fillsInASuppliedReportEvenWhenAStepFails() {
		List<AdoptionStep> steps = List.of(new NamingStep("a", new ArrayList<>()), new ExplodingStep());
		GitHubRepoAdopter adopter = new GitHubRepoAdopter(new RecordingCommandRunner(), steps);
		AdoptionReport report = new AdoptionReport();
		assertThrows(AdoptionException.class, () -> adopter.adopt(context, report));
		assertEquals(List.of("a"), report.completedSteps());
		assertEquals("boom: boom", report.failure().orElseThrow());
		assertFalse(report.succeeded());
	}

	@Test
	void namesTheFailingStepAlongsideTheFailure() {
		AdoptionReport report = new AdoptionReport();
		GitHubRepoAdopter adopter = new GitHubRepoAdopter(new RecordingCommandRunner(),
				List.of(new SilentlyExplodingStep()));
		assertThrows(IllegalStateException.class, () -> adopter.adopt(context, report));
		assertEquals("quiet: IllegalStateException", report.failure().orElseThrow(),
				"an exception with no message must not be reported as a bare null");
	}

	@Test
	void aSuppliedReportIsTheOneReturned() {
		AdoptionReport report = new AdoptionReport();
		GitHubRepoAdopter adopter = new GitHubRepoAdopter(new RecordingCommandRunner(), List.of());
		assertSame(report, adopter.adopt(context, report));
	}

	/**
	 * Every tool is reported missing, so the pipeline aborts in its first step. That
	 * proves the factory wired the default pipeline without letting any later step —
	 * TrustStep writes to the real ~/.claude.json — touch anything outside the test.
	 */
	@Test
	void withDefaultPipelineHonoursPullRequestOptionsAndTheAssetsFlag() {
		RecordingCommandRunner runner = new RecordingCommandRunner(
				command -> new CommandResult(command, 1, "missing"));
		GitHubRepoAdopter adopter = GitHubRepoAdopter.withDefaultPipeline(runner, PullRequestOptions.defaults(), true);
		assertThrows(AdoptionException.class, () -> adopter.adopt(context));
		assertEquals(List.of("git", "--version"), runner.commandAt(0));
	}

	@Test
	void defaultPipelineBranchesCommitsPushesAndOpensAPullRequest() {
		List<String> names = GitHubRepoAdopter.defaultSteps(PullRequestOptions.defaults(), false).stream()
				.map(AdoptionStep::name).toList();
		assertEquals(List.of("toolchain", "clone", "build-toolchain", "branch", "trust", "claude-init", "conform",
				"commit", "enforcer", "commit", "verify", "push", "pull-request"), names);
	}

	@Test
	void defaultPipelineWithAssetsInstallsAndCommitsThemBeforeVerification() {
		List<String> names = GitHubRepoAdopter.defaultSteps(PullRequestOptions.defaults(), true).stream()
				.map(AdoptionStep::name).toList();
		assertEquals(List.of("toolchain", "clone", "build-toolchain", "branch", "trust", "claude-init", "conform",
				"commit", "enforcer", "commit", "assets", "commit", "verify", "push", "pull-request"), names);
	}
}
