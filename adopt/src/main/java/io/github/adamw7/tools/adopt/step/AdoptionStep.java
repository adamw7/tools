package io.github.adamw7.tools.adopt.step;

import io.github.adamw7.tools.adopt.AdoptionContext;
import io.github.adamw7.tools.adopt.AdoptionReport;
import io.github.adamw7.tools.adopt.command.CommandRunner;

/**
 * A single stage of adopting Claude Code into a repository. Steps are ordered
 * and independent: each acts on the shared {@link AdoptionContext} and runs its
 * external commands through the injected {@link CommandRunner}, so the pipeline
 * can be assembled, reordered, or tested one step at a time.
 *
 * <p>The pipeline invokes the three-argument {@link #execute(AdoptionContext,
 * CommandRunner, AdoptionReport)} so a step can contribute facts — such as the
 * opened pull request's URL — to the run's {@link AdoptionReport}. Most steps
 * have nothing to report and only implement the two-argument variant; the
 * default delegation keeps them unaware of the report.
 */
public interface AdoptionStep {

	String name();

	void execute(AdoptionContext context, CommandRunner runner);

	default void execute(AdoptionContext context, CommandRunner runner, AdoptionReport report) {
		execute(context, runner);
	}
}
