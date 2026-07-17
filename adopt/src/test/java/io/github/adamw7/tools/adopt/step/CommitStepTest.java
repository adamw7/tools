package io.github.adamw7.tools.adopt.step;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
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
	void stagesThenCommitsWithMessage() {
		RecordingCommandRunner runner = new RecordingCommandRunner();
		new CommitStep("my message").execute(context, runner);
		assertEquals(List.of("git", "add", "-A"), runner.commandAt(0));
		assertEquals(List.of("git", "commit", "-m", "my message"), runner.commandAt(1));
	}

	@Test
	void emptyCommitIsTreatedAsNoOp() {
		RecordingCommandRunner runner = new RecordingCommandRunner(this::nothingToCommitOnCommit);
		assertDoesNotThrow(() -> new CommitStep("msg").execute(context, runner));
	}

	@Test
	void otherCommitFailureAborts() {
		RecordingCommandRunner runner = new RecordingCommandRunner(this::genuineCommitFailure);
		assertThrows(AdoptionException.class, () -> new CommitStep("msg").execute(context, runner));
	}

	private CommandResult nothingToCommitOnCommit(List<String> command) {
		if (command.contains("commit")) {
			return new CommandResult(command, 1, "nothing to commit, working tree clean");
		}
		return new CommandResult(command, 0, "");
	}

	private CommandResult genuineCommitFailure(List<String> command) {
		if (command.contains("commit")) {
			return new CommandResult(command, 128, "fatal: unable to write commit object");
		}
		return new CommandResult(command, 0, "");
	}
}
