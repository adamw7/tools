package io.github.adamw7.tools.adopt;

import java.util.ArrayList;
import java.util.List;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import io.github.adamw7.tools.adopt.command.CommandRunner;
import io.github.adamw7.tools.adopt.step.AdoptionStep;
import io.github.adamw7.tools.adopt.step.AssetsStep;
import io.github.adamw7.tools.adopt.step.BranchStep;
import io.github.adamw7.tools.adopt.step.ClaudeInitStep;
import io.github.adamw7.tools.adopt.step.ClaudeMdConformanceStep;
import io.github.adamw7.tools.adopt.step.CloneStep;
import io.github.adamw7.tools.adopt.step.CommitStep;
import io.github.adamw7.tools.adopt.step.EnforcerStep;
import io.github.adamw7.tools.adopt.step.PullRequestOptions;
import io.github.adamw7.tools.adopt.step.PullRequestStep;
import io.github.adamw7.tools.adopt.step.PushStep;
import io.github.adamw7.tools.adopt.step.ToolchainStep;
import io.github.adamw7.tools.adopt.step.TrustStep;
import io.github.adamw7.tools.adopt.step.VerifyStep;

/**
 * Runs the ordered pipeline that adopts Claude Code into a GitHub repository:
 * check the required tools are installed, clone, create a feature branch, mark
 * the checkout trusted for Claude Code, generate {@code CLAUDE.md} with
 * {@code claude init}, normalise that file and add a companion {@code AGENTS.md}
 * so it satisfies the guard the adoption is about to wire in, and commit it, wire
 * in the {@code claude-code-enforcer} and
 * commit that, verify the enforcer passes on the generated file, then push the
 * branch and open a pull request. The toolchain check runs first so a missing
 * {@code git}, {@code claude}, or {@code gh} fails the adoption before any
 * expensive work. The adoption never writes to the default branch. Steps and the
 * command runner are injected so the pipeline is easy to reconfigure and to test.
 *
 * <p>Each run returns an {@link AdoptionReport} of the steps that completed and
 * the pull request's URL, so callers can act on the outcome without scraping
 * logs. The default pipeline can optionally include an {@link AssetsStep} that
 * commits starter Claude Code configuration assets alongside the generated
 * {@code CLAUDE.md}.
 */
public class GitHubRepoAdopter {

	private static final Logger log = LogManager.getLogger(GitHubRepoAdopter.class);

	private final CommandRunner runner;
	private final List<AdoptionStep> steps;

	public GitHubRepoAdopter(CommandRunner runner, List<AdoptionStep> steps) {
		this.runner = runner;
		this.steps = List.copyOf(steps);
	}

	public static GitHubRepoAdopter withDefaultPipeline(CommandRunner runner) {
		return new GitHubRepoAdopter(runner, defaultSteps());
	}

	public static GitHubRepoAdopter withDefaultPipeline(CommandRunner runner, PullRequestOptions options,
			boolean includeAssets) {
		return new GitHubRepoAdopter(runner, defaultSteps(options, includeAssets));
	}

	public static List<AdoptionStep> defaultSteps() {
		return defaultSteps(PullRequestOptions.defaults(), false);
	}

	public static List<AdoptionStep> defaultSteps(PullRequestOptions options, boolean includeAssets) {
		List<AdoptionStep> steps = new ArrayList<>(List.of(
				new ToolchainStep(),
				new CloneStep(),
				new BranchStep(),
				new TrustStep(),
				new ClaudeInitStep(),
				new ClaudeMdConformanceStep(),
				new CommitStep("Adopt Claude Code: add CLAUDE.md"),
				new EnforcerStep(),
				new CommitStep("Add claude-code-enforcer to the build")));
		if (includeAssets) {
			steps.add(new AssetsStep());
			steps.add(new CommitStep("Add Claude Code configuration assets"));
		}
		steps.add(new VerifyStep());
		steps.add(new PushStep());
		steps.add(new PullRequestStep(options));
		return List.copyOf(steps);
	}

	public AdoptionReport adopt(AdoptionContext context) {
		log.info("Adopting Claude Code into {}", context.repositoryUrl());
		AdoptionReport report = new AdoptionReport();
		for (AdoptionStep step : steps) {
			runStep(step, context, report);
		}
		log.info("Adoption complete for {}", context.repositoryUrl());
		return report;
	}

	private void runStep(AdoptionStep step, AdoptionContext context, AdoptionReport report) {
		log.info("Step: {}", step.name());
		step.execute(context, runner, report);
		report.recordStep(step.name());
	}
}
