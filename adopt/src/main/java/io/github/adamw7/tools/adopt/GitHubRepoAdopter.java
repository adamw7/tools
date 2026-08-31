package io.github.adamw7.tools.adopt;

import java.util.List;
import java.util.stream.Stream;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import io.github.adamw7.tools.adopt.command.CommandRunner;
import io.github.adamw7.tools.adopt.step.AdoptionCheckStep;
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
import io.github.adamw7.tools.adopt.step.SkillsStep;
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
 * <p>Both toolchain checks fail early: the pipeline's own tools before any expensive
 * work, the project's build tool as soon as the clone reveals which one it is. Each
 * run returns an {@link AdoptionReport} of the steps completed and the pull request's
 * URL; a caller needing the report of a run that <em>fails</em> supplies its own to
 * {@link #adopt(AdoptionContext, AdoptionReport)}. What the default pipeline contains
 * is decided by {@link AdoptionOptions}: an {@link AssetsStep} and a {@link SkillsStep}
 * on request, and a dry run leaves out the two steps that write to GitHub.
 */
public class GitHubRepoAdopter {

	/**
	 * What the guard commit says it did. It names the guard rather than the artifact
	 * supplying one of them: which guard {@link EnforcerStep} wires in is the
	 * checkout's build system to decide, and only the Maven path adds the
	 * {@code claude-code-enforcer}. A message naming the enforcer landed in the history
	 * of repositories given a Gradle task instead.
	 */
	static final String GUARD_COMMIT_MESSAGE = "Adopt Claude Code: add the CLAUDE.md guard";

	/**
	 * What the starter-assets commit says it did, the skills included. It is named beside
	 * the guard's rather than spelled out where the step is assembled, because a run
	 * leaves two commits in somebody else's history and a test asking what each of them
	 * carried has to name the one it means.
	 */
	static final String ASSETS_COMMIT_MESSAGE = "Add Claude Code configuration assets";

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
	/**
	 * The steps that answer "is this repository still adopted, and does its guard still
	 * pass?" without adopting anything: check the tools, clone, check the project's
	 * build tool, ask whether the document and the guard are there, and run the guard.
	 * A separate pipeline rather than an adoption with its writing steps disabled, for
	 * the same reason a dry run is: the report then says what the run really did.
	 */
	public static List<AdoptionStep> verificationSteps(AdoptionOptions options) {
		List<BuildSystem> buildSystems = BuildSystems.defaults(options.guard());
		return List.of(
				ToolchainStep.forVerification(),
				new CloneStep(),
				new BuildToolchainStep(buildSystems),
				new AdoptionCheckStep(buildSystems),
				new VerifyStep(buildSystems));
	}

	/** The pipeline the options describe: a verification, or an adoption. */
	public static GitHubRepoAdopter forRun(CommandRunner runner, AdoptionOptions options) {
		return new GitHubRepoAdopter(runner,
				options.verifyOnly() ? verificationSteps(options) : defaultSteps(options));
	}

	public static List<AdoptionStep> defaultSteps(AdoptionOptions options) {
		List<BuildSystem> buildSystems = BuildSystems.defaults(options.guard());
		return Stream.of(
				adoptionSteps(buildSystems, options),
				assetSteps(buildSystems, options),
				List.<AdoptionStep>of(new VerifyStep(buildSystems)),
				publicationSteps(options))
				.flatMap(List::stream)
				.toList();
	}

	private static List<AdoptionStep> adoptionSteps(List<BuildSystem> buildSystems, AdoptionOptions options) {
		return List.of(
				toolchainStep(options),
				new CloneStep(),
				new BuildToolchainStep(buildSystems),
				new BranchStep(),
				new TrustStep(),
				ClaudeInitStep.retrying(options.retries()),
				new ClaudeMdConformanceStep(buildSystems),
				new CommitStep("Adopt Claude Code: add CLAUDE.md", "claude-md"),
				new EnforcerStep(buildSystems),
				new CommitStep(GUARD_COMMIT_MESSAGE, "guard"));
	}

	/**
	 * The toolchain check is asked for exactly what the assembled pipeline will run,
	 * so the two stay in step: a dry run that leaves out the pull request is not held
	 * to the {@code gh} that only the pull request uses.
	 */
	private static AdoptionStep toolchainStep(AdoptionOptions options) {
		return options.dryRun() ? ToolchainStep.forDryRun() : new ToolchainStep();
	}

	/**
	 * The starter configuration is written by two steps and committed by one: the
	 * assets that are the same everywhere, then the skills, whose bodies name the
	 * build system the guard was wired into. One commit rather than two, so a run
	 * still leaves exactly two commits in somebody else's history.
	 */
	private static List<AdoptionStep> assetSteps(List<BuildSystem> buildSystems, AdoptionOptions options) {
		if (!options.includeAssets()) {
			return List.of();
		}
		return List.of(new AssetsStep(), new SkillsStep(buildSystems),
				new CommitStep(ASSETS_COMMIT_MESSAGE, "assets"));
	}

	/**
	 * A dry run ends at the verification, so the pipeline is assembled without the
	 * two steps that write to GitHub rather than with steps that decide for
	 * themselves to do nothing. The report then says what a dry run really did,
	 * stopping at {@code verify} instead of listing a {@code push} and a
	 * {@code pull-request} that only pretended to run. Everything before it still
	 * happens for real, so the operator has the adoption's commits to read.
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
	 * Runs the pipeline into a report the caller already holds, so the outcome survives
	 * a failure: one handed back only on the return path is lost the moment a step
	 * throws, exactly when a caller wants to know which steps completed.
	 *
	 * @param report filled in as the run progresses, and marked as failed before a
	 *               failing step's exception propagates
	 * @return the same report, once every step has completed
	 */
	/** The steps this adopter runs, so a test can assert which pipeline it was given. */
	List<AdoptionStep> steps() {
		return steps;
	}

	public AdoptionReport adopt(AdoptionContext context, AdoptionReport report) {
		log.info("Adopting Claude Code into {} in {} steps", context.displayUrl(), steps.size());
		Elapsed elapsed = Elapsed.started();
		for (int index = 0; index < steps.size(); index++) {
			runStep(steps.get(index), index + 1, context, report);
		}
		log.info("Adoption complete for {} in {}", context.displayUrl(), elapsed);
		return report;
	}

	/**
	 * Each step is announced with its position and closed with what it cost, the two
	 * questions an operator has of a run still going being how much is left and whether
	 * the current step is working or stuck. A failing step is named here too, without
	 * its stack trace: {@link BatchAdoption} logs the exception once, but nothing there
	 * says which stage produced it.
	 */
	private void runStep(AdoptionStep step, int ordinal, AdoptionContext context, AdoptionReport report) {
		log.info("Step {}/{}: {}", ordinal, steps.size(), step.name());
		Elapsed elapsed = Elapsed.started();
		try {
			step.execute(context, runner, report);
		} catch (RuntimeException e) {
			log.warn("Step {} failed after {}", step.name(), elapsed);
			report.recordFailure(describe(step, e));
			throw e;
		}
		log.info("Step {} completed in {}", step.name(), elapsed);
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
