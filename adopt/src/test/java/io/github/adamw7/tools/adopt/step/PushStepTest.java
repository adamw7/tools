package io.github.adamw7.tools.adopt.step;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.nio.file.Path;
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
		assertEquals(List.of("git", "-c", "remote.origin.pushurl=https://github.com/adamw7/demo.git",
				"push", "-u", "origin", "claude/adopt-claude-code"), runner.commandAt(0));
		assertEquals(context.repositoryDirectory(), runner.invocations().get(0).workingDirectory());
	}

	/**
	 * The push is the one command that has to authenticate as the operator, and
	 * {@link CloneStep} deliberately leaves no credentials in the checkout for it to
	 * pick up — so it must carry them itself, or an adoption driven by a token would
	 * clone and commit perfectly well and then be refused at the last step.
	 */
	@Test
	void carriesTheRunsCredentialsToTheRemote() {
		RecordingCommandRunner runner = new RecordingCommandRunner();
		new PushStep().execute(credentialled(), runner);
		assertEquals("remote.origin.pushurl=https://x-access-token:TOKEN@github.com/adamw7/demo.git",
				runner.commandAt(0).get(2));
	}

	/**
	 * Passed as a per-invocation {@code -c} override rather than written to the
	 * checkout's configuration: git applies it to this push alone, so the token still
	 * does not outlive the run.
	 */
	@Test
	void neverConfiguresTheCredentialsIntoTheCheckout() {
		RecordingCommandRunner runner = new RecordingCommandRunner();
		new PushStep().execute(credentialled(), runner);
		assertEquals(1, runner.invocations().size(), "the push is the only command: nothing configures the remote");
		assertEquals(List.of("git", "-c"), runner.commandAt(0).subList(0, 2),
				"the override belongs to the invocation, not to .git/config");
	}

	private AdoptionContext credentialled() {
		return new AdoptionContext("https://x-access-token:TOKEN@github.com/adamw7/demo.git", Path.of("/tmp/workspace"));
	}

	@Test
	void rejectedPushAborts() {
		RecordingCommandRunner runner = RecordingCommandRunner.failing(1, "rejected");
		assertThrows(AdoptionException.class, () -> step.execute(context, runner));
	}
}
