package io.github.adamw7.tools.adopt.step;

import io.github.adamw7.tools.adopt.AdoptionContext;
import io.github.adamw7.tools.adopt.command.CommandRunner;

/**
 * A single stage of adopting Claude Code into a repository. Steps are ordered
 * and independent: each acts on the shared {@link AdoptionContext} and runs its
 * external commands through the injected {@link CommandRunner}, so the pipeline
 * can be assembled, reordered, or tested one step at a time.
 */
public interface AdoptionStep {

	String name();

	void execute(AdoptionContext context, CommandRunner runner);
}
