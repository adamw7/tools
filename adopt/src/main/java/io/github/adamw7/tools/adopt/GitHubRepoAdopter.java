package io.github.adamw7.tools.adopt;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import io.github.adamw7.tools.adopt.command.CommandRunner;
import io.github.adamw7.tools.adopt.step.AdoptionStep;
import io.github.adamw7.tools.adopt.step.AssetsStep;
import io.github.adamw7.tools.adopt.step.BranchStep;
import io.github.adamw7.tools.adopt.step.BuildSystem;
import io.github.adamw7.tools.adopt.step.BuildSystems;
import io.github.adamw7.tools.adopt.step.BuildToolchainStep;
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
 * check the required tools are installed, clone, check the cloned project's own
 * build tool, create a feature branch, mark the checkout trusted for Claude Code,
 * generate {@code CLAUDE.md} with {@code claude init}, conform it and commit,
 * wire in the {@code claude-code-enforcer} and commit that, verify the guard
 * passes, then push the branch and open a pull request. The default branch is
 * never written to. Steps and the command runner are injected, so the pipeline is
 * easy to reconfigure and to test.
 *
 * <p>Both toolchain checks are placed to fail early: the pipeline's own tools
 * before any expensive work, the project's build tool as soon as the clone
 * reveals which one it is, rather than at the verification. Each run returns an
 * {@link AdoptionReport} of the steps that completed and the pull request's URL;
 * a caller that needs the report of a run that <em>fails</em> supplies its own to
 * {@link #adopt(AdoptionContext, AdoptionReport)} and still holds it afterwards.
 * The default pipeline optionally includes an {@link AssetsStep}.
 */
public class GitHubRepoAdopter {

	private static final Logger log = LogManager.getLogger(GitHubRepoAdopter.class);

	private final CommandRunner runner;
	private final List<AdoptionStep> steps;

	public GitHubRepoAdopter(CommandRunner runner, List<AdoptionStep> steps) {
		this.runner = runner;
		this.steps = List.copyOf(steps);
	}

	/**
	 * @param ruleVersion the released {@code claude-code-enforcer} version a Maven
	 *                    project's POM should pin, or empty to resolve the version of
	 *                    the {@code tools} build running the adoption
	 */
	public static GitHubRepoAdopter withDefaultPipeline(CommandRunner runner, PullRequestOptions options,
			boolean includeAssets, Optional<String> ruleVersion) {
		return new GitHubRepoAdopter(runner, defaultSteps(options, includeAssets, ruleVersion));
	}

	/**
	 * The three steps that act on the checkout's build system are given the same
	 * build-system list, so the guard that is wired in is the guard that is verified
	 * with the tool that was probed.
	 */
	public static List<AdoptionStep> defaultSteps(PullRequestOptions options, boolean includeAssets,
			Optional<String> ruleVersion) {
		List<BuildSystem> buildSystems = BuildSystems.defaults(ruleVersion);
		List<AdoptionStep> steps = new ArrayList<>(List.of(
				new ToolchainStep(),
				new CloneStep(),
				new BuildToolchainStep(buildSystems),
				new BranchStep(),
				new TrustStep(),
				new ClaudeInitStep(),
				new ClaudeMdConformanceStep(),
				new CommitStep("Adopt Claude Code: add CLAUDE.md"),
				new EnforcerStep(buildSystems),
				new CommitStep("Add claude-code-enforcer to the build")));
		if (includeAssets) {
			steps.add(new AssetsStep());
			steps.add(new CommitStep("Add Claude Code configuration assets"));
		}
		steps.add(new VerifyStep(buildSystems));
		steps.add(new PushStep());
		steps.add(new PullRequestStep(options));
		return List.copyOf(steps);
	}

	/**
	 * Runs the pipeline into a report the caller already holds, so the outcome
	 * survives a failure: a report created here and only handed back on the return
	 * path is lost the moment a step throws, which is exactly when a caller wants to
	 * know which steps completed.
	 *
	 * @param report filled in as the run progresses, and marked as failed before a
	 *               failing step's exception propagates
	 * @return the same report, once every step has completed
	 */
	public AdoptionReport adopt(AdoptionContext context, AdoptionReport report) {
		log.info("Adopting Claude Code into {}", context.displayUrl());
		for (AdoptionStep step : steps) {
			runStep(step, context, report);
		}
		log.info("Adoption complete for {}", context.displayUrl());
		return report;
	}

	private void runStep(AdoptionStep step, AdoptionContext context, AdoptionReport report) {
		log.info("Step: {}", step.name());
		try {
			step.execute(context, runner, report);
		} catch (RuntimeException e) {
			report.recordFailure(describe(step, e));
			throw e;
		}
		report.recordStep(step.name());
	}

	/**
	 * Names the failing step alongside the failure, because a message alone —
	 * {@code "The requested URL returned error: 403"} — does not say which stage
	 * produced it.
	 */
	private String describe(AdoptionStep step, RuntimeException failure) {
		return step.name() + ": " + Failures.describe(failure);
	}
}
