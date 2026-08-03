package io.github.adamw7.tools.adopt.step;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

import io.github.adamw7.tools.adopt.AdoptionContext;
import io.github.adamw7.tools.adopt.AdoptionContexts;
import io.github.adamw7.tools.adopt.AdoptionException;
import io.github.adamw7.tools.adopt.command.CommandResult;
import io.github.adamw7.tools.adopt.command.RecordingCommandRunner;

class CommitStepTest {

	private final AdoptionContext context = AdoptionContexts.of();

	@Test
	void stagesChecksThenCommitsWithMessage() {
		RecordingCommandRunner runner = new RecordingCommandRunner(this::stagedChanges);
		new CommitStep("my message").execute(context, runner);
		assertEquals(List.of("git", "add", "-A"), runner.commandAt(0));
		assertEquals(List.of("git", "diff", "--cached", "--quiet"), runner.commandAt(1));
		assertEquals(List.of("git", "config", "--get", "user.name"), runner.commandAt(2));
		assertEquals(List.of("git", "config", "--get", "user.email"), runner.commandAt(3));
		assertEquals(List.of("git", "commit", "-m", "my message"), runner.commandAt(4));
	}

	@Test
	void suppliesAFallbackIdentityWhenGitHasNone() {
		RecordingCommandRunner runner = new RecordingCommandRunner(this::stagedChangesWithoutIdentity);
		new CommitStep("my message").execute(context, runner);
		assertEquals(List.of("git", "-c", "user.name=" + CommitStep.FALLBACK_NAME, "-c",
				"user.email=" + CommitStep.FALLBACK_EMAIL, "commit", "-m", "my message"), runner.commandAt(4));
	}

	@Test
	void leavesAConfiguredIdentityInForce() {
		RecordingCommandRunner runner = new RecordingCommandRunner(this::stagedChanges);
		new CommitStep("my message").execute(context, runner);
		assertEquals(List.of("git", "commit", "-m", "my message"), runner.commandAt(4));
	}

	@Test
	void skipsCommitWhenNothingIsStaged() {
		RecordingCommandRunner runner = new RecordingCommandRunner();
		new CommitStep("msg").execute(context, runner);
		assertEquals(2, runner.count());
		assertEquals(List.of("git", "diff", "--cached", "--quiet"), runner.commandAt(1));
	}

	@Test
	void commitFailureAborts() {
		RecordingCommandRunner runner = new RecordingCommandRunner(this::genuineCommitFailure);
		assertThrows(AdoptionException.class, () -> new CommitStep("msg").execute(context, runner));
	}

	@Test
	void reportsTheBareNameWhenUnqualified() {
		assertEquals("commit", new CommitStep("msg").name());
		assertEquals("commit", new CommitStep("msg", " ").name());
		assertEquals("commit", new CommitStep("msg", null).name());
	}

	/**
	 * The pipeline runs this step two or three times, so a report's
	 * {@code completedSteps} has to say which of the adoption's commits landed rather
	 * than repeating one name.
	 */
	@Test
	void qualifiesTheStepNameWithWhatItCommits() {
		assertEquals("commit:claude-md", new CommitStep("msg", "claude-md").name());
		assertEquals("commit:guard", new CommitStep("msg", "  guard  ").name());
	}

	/** The qualified name is what a failing commit reports, so the failure names the same step. */
	@Test
	void namesTheQualifiedStepInAFailure() {
		RecordingCommandRunner runner = new RecordingCommandRunner(this::genuineCommitFailure);
		AdoptionException failure = assertThrows(AdoptionException.class,
				() -> new CommitStep("msg", "guard").execute(context, runner));
		assertTrue(failure.getMessage().startsWith("commit:guard failed"), failure.getMessage());
	}

	private CommandResult stagedChanges(List<String> command) {
		if (command.contains("diff")) {
			return new CommandResult(command, 1, "");
		}
		return new CommandResult(command, 0, "");
	}

	private CommandResult stagedChangesWithoutIdentity(List<String> command) {
		if (command.contains("diff")) {
			return new CommandResult(command, 1, "");
		}
		if (command.contains("config")) {
			return new CommandResult(command, 1, "");
		}
		return new CommandResult(command, 0, "");
	}

	private CommandResult genuineCommitFailure(List<String> command) {
		if (command.contains("diff")) {
			return new CommandResult(command, 1, "");
		}
		if (command.contains("commit")) {
			return new CommandResult(command, 128, "fatal: unable to write commit object");
		}
		return new CommandResult(command, 0, "");
	}
}
