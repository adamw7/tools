package io.github.adamw7.tools.adopt.step;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;

import org.junit.jupiter.api.Test;

import io.github.adamw7.tools.adopt.AdoptionContext;
import io.github.adamw7.tools.adopt.AdoptionContexts;
import io.github.adamw7.tools.adopt.AdoptionException;
import io.github.adamw7.tools.adopt.command.RecordingCommandRunner;

class PushStepTest {

	private final AdoptionContext context = AdoptionContexts.of();
	private final PushStep step = new PushStep();

	@Test
	void pushesFeatureBranchWithUpstreamInCheckout() {
		RecordingCommandRunner runner = new RecordingCommandRunner();
		step.execute(context, runner);
		assertEquals(List.of("git", "push", "-u", "origin", "claude/adopt-claude-code"), runner.commandAt(0));
		assertEquals(context.repositoryDirectory(), runner.invocations().get(0).workingDirectory());
	}

	@Test
	void rejectedPushAborts() {
		RecordingCommandRunner runner = RecordingCommandRunner.failing(1, "rejected");
		assertThrows(AdoptionException.class, () -> step.execute(context, runner));
	}
}
