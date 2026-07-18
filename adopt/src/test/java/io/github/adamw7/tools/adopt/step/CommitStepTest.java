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

class CommitStepTest {

	private final AdoptionContext context = new AdoptionContext("https://github.com/adamw7/tools.git",
			Path.of("/tmp/workspace"));

	@Test
	void stagesChecksThenCommitsWithMessage() {
		RecordingCommandRunner runner = new RecordingCommandRunner(this::stagedChanges);
		new CommitStep("my message").execute(context, runner);
		assertEquals(List.of("git", "add", "-A"), runner.commandAt(0));
		assertEquals(List.of("git", "diff", "--cached", "--quiet"), runner.commandAt(1));
		assertEquals(List.of("git", "commit", "-m", "my message"), runner.commandAt(2));
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

	private CommandResult stagedChanges(List<String> command) {
		if (command.contains("diff")) {
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
