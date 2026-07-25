package io.github.adamw7.tools.adopt.step;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.nio.file.Path;
import java.util.List;
import java.util.function.Function;

import org.junit.jupiter.api.Test;

import io.github.adamw7.tools.adopt.AdoptionContext;
import io.github.adamw7.tools.adopt.AdoptionException;
import io.github.adamw7.tools.adopt.command.CommandResult;
import io.github.adamw7.tools.adopt.command.RecordingCommandRunner;

class BranchStepTest {

	private final AdoptionContext context = new AdoptionContext("https://github.com/adamw7/tools.git",
			Path.of("/tmp/workspace"), "claude/adopt-claude-code");
	private final BranchStep step = new BranchStep();

	@Test
	void createsFeatureBranchInCheckout() {
		RecordingCommandRunner runner = new RecordingCommandRunner(missingRefs());
		step.execute(context, runner);
		assertEquals(List.of("git", "checkout", "-B", "claude/adopt-claude-code"), lastCommand(runner));
		assertEquals(context.repositoryDirectory(), runner.invocations().get(0).workingDirectory());
	}

	/**
	 * A fresh clone re-adopting a repository an earlier run already pushed carries
	 * no local branch but does carry origin's. Starting from that published tip is
	 * what keeps the later push a fast-forward instead of a rejected one.
	 */
	@Test
	void resumesThePublishedBranchWhenTheCheckoutHasNoLocalOne() {
		RecordingCommandRunner runner = new RecordingCommandRunner(
				refs(List.of("refs/remotes/origin/claude/adopt-claude-code")));
		step.execute(context, runner);
		assertEquals(List.of("git", "checkout", "-B", "claude/adopt-claude-code",
				"origin/claude/adopt-claude-code"), lastCommand(runner));
	}

	@Test
	void keepsALocalBranchRatherThanResettingItOntoTheRemote() {
		RecordingCommandRunner runner = new RecordingCommandRunner(refs(List.of(
				"refs/heads/claude/adopt-claude-code", "refs/remotes/origin/claude/adopt-claude-code")));
		step.execute(context, runner);
		assertEquals(List.of("git", "checkout", "-B", "claude/adopt-claude-code"), lastCommand(runner));
	}

	private Function<List<String>, CommandResult> missingRefs() {
		return refs(List.of());
	}

	/** Answers {@code git rev-parse --verify} for the named refs only, as git does. */
	private Function<List<String>, CommandResult> refs(List<String> existing) {
		return command -> new CommandResult(command, resolves(command, existing) ? 0 : 1, "");
	}

	private boolean resolves(List<String> command, List<String> existing) {
		return !command.contains("rev-parse") || existing.contains(command.get(command.size() - 1));
	}

	private List<String> lastCommand(RecordingCommandRunner runner) {
		return runner.commandAt(runner.count() - 1);
	}

	@Test
	void failedCheckoutAbortsAdoption() {
		RecordingCommandRunner runner = new RecordingCommandRunner(
				command -> new CommandResult(command, 128, "fatal: a branch named ... already exists"));
		assertThrows(AdoptionException.class, () -> step.execute(context, runner));
	}

	@Test
	void isNamedBranch() {
		assertEquals("branch", step.name());
	}
}
