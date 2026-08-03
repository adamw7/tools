package io.github.adamw7.tools.adopt;

import java.util.List;
import java.util.stream.Stream;

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
 * What the default pipeline contains is decided by {@link AdoptionOptions}: an
 * {@link AssetsStep} is included on request, and a dry run leaves out the two
 * steps that write to GitHub.
 */
public class GitHubRepoAdopter {

	private static final Logger log = LogManager.getLogger(GitHubRepoAdopter.class);

	private final CommandRunner runner;
	private final List<AdoptionStep> steps;

	public GitHubRepoAdopter(CommandRunner runner, List<AdoptionStep> steps) {
		this.runner = runner;
		this.steps = List.copyOf(steps);
	}

	public static GitHubRepoAdopter withDefaultPipeline(CommandRunner runner, AdoptionOptions options) {
		return new GitHubRepoAdopter(runner, defaultSteps(options));
	}

	/**
	 * The three steps that act on the checkout's build system are given the same
	 * build-system list, so the guard that is wired in is the guard that is verified
	 * with the tool that was probed.
	 */
	public static List<AdoptionStep> defaultSteps(AdoptionOptions options) {
		List<BuildSystem> buildSystems = BuildSystems.defaults(options.pinnedRuleVersion());
		return Stream.of(
				adoptionSteps(buildSystems),
				assetSteps(options),
				List.<AdoptionStep>of(new VerifyStep(buildSystems)),
				publicationSteps(options))
				.flatMap(List::stream)
				.toList();
	}

	private static List<AdoptionStep> adoptionSteps(List<BuildSystem> buildSystems) {
		return List.of(
				new ToolchainStep(),
				new CloneStep(),
				new BuildToolchainStep(buildSystems),
				new BranchStep(),
				new TrustStep(),
				new ClaudeInitStep(),
				new ClaudeMdConformanceStep(),
				new CommitStep("Adopt Claude Code: add CLAUDE.md", "claude-md"),
				new EnforcerStep(buildSystems),
				new CommitStep("Add claude-code-enforcer to the build", "guard"));
	}

	private static List<AdoptionStep> assetSteps(AdoptionOptions options) {
		if (!options.includeAssets()) {
			return List.of();
		}
		return List.of(new AssetsStep(), new CommitStep("Add Claude Code configuration assets", "assets"));
	}

	/**
	 * A dry run ends at the verification, so the pipeline is assembled without the
	 * two steps that write to GitHub rather than with steps that decide for
	 * themselves to do nothing. The report then says what a dry run really did: the
	 * steps it completed stop at {@code verify}, instead of listing a {@code push}
	 * and a {@code pull-request} that only pretended to run.
	 *
	 * <p>Everything before the verification still happens for real — the checkout is
	 * cloned, branched, and committed on — so the operator has the adoption's commits
	 * in the workspace to read before any of it is published.
	 */
	private static List<AdoptionStep> publicationSteps(AdoptionOptions options) {
		if (options.dryRun()) {
			log.info("Dry run: the adoption will be committed to the checkout but never pushed,"
					+ " and no pull request will be opened");
			return List.of();
		}
		return List.of(new PushStep(), new PullRequestStep(options.pullRequest()));
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
