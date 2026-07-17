package io.github.adamw7.tools.adopt;

import java.util.List;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import io.github.adamw7.tools.adopt.command.CommandRunner;
import io.github.adamw7.tools.adopt.step.AdoptionStep;
import io.github.adamw7.tools.adopt.step.ClaudeInitStep;
import io.github.adamw7.tools.adopt.step.CloneStep;
import io.github.adamw7.tools.adopt.step.CommitStep;
import io.github.adamw7.tools.adopt.step.EnforcerStep;
import io.github.adamw7.tools.adopt.step.PushStep;
import io.github.adamw7.tools.adopt.step.TrustStep;

/**
 * Runs the ordered pipeline that adopts Claude Code into a GitHub repository:
 * clone, mark the checkout trusted for Claude Code, generate {@code CLAUDE.md}
 * with {@code claude init}, commit and push that, then wire in the
 * {@code claude-code-enforcer} and commit and push again. Steps and the command
 * runner are injected so the pipeline is easy to reconfigure and to test.
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

	public static List<AdoptionStep> defaultSteps() {
		return List.of(
				new CloneStep(),
				new TrustStep(),
				new ClaudeInitStep(),
				new CommitStep("Adopt Claude Code: add CLAUDE.md"),
				new PushStep(),
				new EnforcerStep(),
				new CommitStep("Add claude-code-enforcer to the build"),
				new PushStep());
	}

	public void adopt(AdoptionContext context) {
		log.info("Adopting Claude Code into {}", context.repositoryUrl());
		for (AdoptionStep step : steps) {
			runStep(step, context);
		}
		log.info("Adoption complete for {}", context.repositoryUrl());
	}

	private void runStep(AdoptionStep step, AdoptionContext context) {
		log.info("Step: {}", step.name());
		step.execute(context, runner);
	}
}
