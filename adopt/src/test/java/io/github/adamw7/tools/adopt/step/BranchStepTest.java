package io.github.adamw7.tools.adopt.step;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.nio.file.Path;
import java.util.List;

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
		RecordingCommandRunner runner = new RecordingCommandRunner();
		step.execute(context, runner);
		assertEquals(List.of("git", "checkout", "-B", "claude/adopt-claude-code"), runner.commandAt(0));
		assertEquals(context.repositoryDirectory(), runner.invocations().get(0).workingDirectory());
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
