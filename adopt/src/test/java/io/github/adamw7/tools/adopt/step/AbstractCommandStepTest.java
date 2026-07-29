package io.github.adamw7.tools.adopt.step;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;

import io.github.adamw7.tools.adopt.AdoptionContext;
import io.github.adamw7.tools.adopt.AdoptionException;
import io.github.adamw7.tools.adopt.command.CommandResult;
import io.github.adamw7.tools.adopt.command.CommandRunner;
import io.github.adamw7.tools.adopt.command.RecordingCommandRunner;

class AbstractCommandStepTest {

	private static final class TestStep extends AbstractCommandStep {

		@Override
		public String name() {
			return "test-step";
		}

		@Override
		public void execute(AdoptionContext context, CommandRunner runner) {
			// not exercised here; the base run-and-fail-fast behaviour is under test
		}

		CommandResult run(CommandRunner runner, Path workingDirectory, List<String> command) {
			return runOrFail(runner, workingDirectory, command);
		}
	}

	private final Path workspace = Path.of("/tmp/workspace");
	private final TestStep step = new TestStep();

	@Test
	void returnsResultWhenCommandSucceeds() {
		RecordingCommandRunner runner = RecordingCommandRunner.answering("done");
		CommandResult result = step.run(runner, workspace, List.of("git", "status"));
		assertEquals("done", result.output());
		assertEquals(List.of("git", "status"), runner.commandAt(0));
	}

	@Test
	void failedCommandThrowsAdoptionException() {
		RecordingCommandRunner runner = RecordingCommandRunner.failing(1, "rejected");
		assertThrows(AdoptionException.class,
				() -> step.run(runner, workspace, List.of("git", "push")));
	}

	@Test
	void failureMessageCarriesStepNameExitCodeCommandAndOutput() {
		RecordingCommandRunner runner = RecordingCommandRunner.failing(128, "fatal: rejected");
		AdoptionException exception = assertThrows(AdoptionException.class,
				() -> step.run(runner, workspace, List.of("git", "push", "origin", "main")));
		String message = exception.getMessage();
		assertTrue(message.contains("test-step"), message);
		assertTrue(message.contains("128"), message);
		assertTrue(message.contains("git push origin main"), message);
		assertTrue(message.contains("fatal: rejected"), message);
	}

	/**
	 * A tool that fails on a credentialled clone URL echoes it back, and this message
	 * becomes the run's reported failure — so neither the command nor its transcript
	 * may carry the secret out of the step.
	 */
	@Test
	void failureMessageMasksCredentialsInTheCommandAndTheTranscript() {
		RecordingCommandRunner runner = new RecordingCommandRunner(command -> new CommandResult(command, 128,
				"fatal: could not read Username for 'https://x-access-token:secret@github.com'"));
		AdoptionException exception = assertThrows(AdoptionException.class, () -> step.run(runner, workspace,
				List.of("git", "clone", "https://x-access-token:secret@github.com/owner/repo.git")));
		String message = exception.getMessage();
		assertTrue(message.contains("git clone https://***@github.com/owner/repo.git"), message);
		assertTrue(message.contains("Username for 'https://***@github.com'"), message);
		assertTrue(!message.contains("secret"), message);
	}
}
