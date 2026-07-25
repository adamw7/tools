package io.github.adamw7.tools.adopt;

import java.util.ArrayList;
import java.util.List;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import io.github.adamw7.tools.adopt.command.CommandRunner;
import io.github.adamw7.tools.adopt.step.AdoptionStep;
import io.github.adamw7.tools.adopt.step.AssetsStep;
import io.github.adamw7.tools.adopt.step.BranchStep;
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
 * build tool is installed too, create a feature branch, mark
 * the checkout trusted for Claude Code, generate {@code CLAUDE.md} with
 * {@code claude init}, normalise that file and add a companion {@code AGENTS.md}
 * so it satisfies the guard the adoption is about to wire in, and commit it, wire
 * in the {@code claude-code-enforcer} and
 * commit that, verify the enforcer passes on the generated file, then push the
 * branch and open a pull request. The toolchain check runs first so a missing
 * {@code git}, {@code claude}, or {@code gh} fails the adoption before any
 * expensive work, and the build-tool check follows the clone — the first moment
 * the project's build system is known — so a missing {@code mvn} or {@code gradle}
 * fails before the {@code claude init} rather than at the verification.
 * The adoption never writes to the default branch. Steps and the
 * command runner are injected so the pipeline is easy to reconfigure and to test.
 *
 * <p>Each run returns an {@link AdoptionReport} of the steps that completed and
 * the pull request's URL, so callers can act on the outcome without scraping
 * logs. A caller that needs the report of a run that <em>fails</em> — the case
 * where knowing how far the pipeline got matters most — supplies its own report
 * to {@link #adopt(AdoptionContext, AdoptionReport)} and still holds it after the
 * failure propagates. The default pipeline can optionally include an
 * {@link AssetsStep} that commits starter Claude Code configuration assets
 * alongside the generated {@code CLAUDE.md}.
 */
public class GitHubRepoAdopter {

	private static final Logger log = LogManager.getLogger(GitHubRepoAdopter.class);

	private final CommandRunner runner;
	private final List<AdoptionStep> steps;

	public GitHubRepoAdopter(CommandRunner runner, List<AdoptionStep> steps) {
		this.runner = runner;
		this.steps = List.copyOf(steps);
	}

	public static GitHubRepoAdopter withDefaultPipeline(CommandRunner runner, PullRequestOptions options,
			boolean includeAssets) {
		return new GitHubRepoAdopter(runner, defaultSteps(options, includeAssets));
	}

	public static List<AdoptionStep> defaultSteps(PullRequestOptions options, boolean includeAssets) {
		List<AdoptionStep> steps = new ArrayList<>(List.of(
				new ToolchainStep(),
				new CloneStep(),
				new BuildToolchainStep(),
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
		return adopt(context, new AdoptionReport());
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
		log.info("Adopting Claude Code into {}", context.repositoryUrl());
		for (AdoptionStep step : steps) {
			runStep(step, context, report);
		}
		log.info("Adoption complete for {}", context.repositoryUrl());
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
	 * Names the failing step alongside the failure, because an exception message
	 * alone — {@code "The requested URL returned error: 403"} — does not say which
	 * stage of the adoption produced it. An exception carrying no message at all
	 * falls back to its type, so the report never records a bare {@code null}.
	 */
	private String describe(AdoptionStep step, RuntimeException failure) {
		String message = failure.getMessage();
		return step.name() + ": " + (message == null || message.isBlank()
				? failure.getClass().getSimpleName()
				: message);
	}
}
