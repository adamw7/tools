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

class PushStepTest {

	private final AdoptionContext context = new AdoptionContext("https://github.com/adamw7/tools.git",
			Path.of("/tmp/workspace"));
	private final PushStep step = new PushStep();

	@Test
	void runsGitPushInCheckout() {
		RecordingCommandRunner runner = new RecordingCommandRunner();
		step.execute(context, runner);
		assertEquals(List.of("git", "push"), runner.commandAt(0));
		assertEquals(context.repositoryDirectory(), runner.invocations().get(0).workingDirectory());
	}

	@Test
	void rejectedPushAborts() {
		RecordingCommandRunner runner = new RecordingCommandRunner(
				command -> new CommandResult(command, 1, "rejected"));
		assertThrows(AdoptionException.class, () -> step.execute(context, runner));
	}
}
