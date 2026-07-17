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

class CloneStepTest {

	private final Path workspace = Path.of("/tmp/workspace");
	private final AdoptionContext context = new AdoptionContext("https://github.com/adamw7/tools.git", workspace);
	private final CloneStep step = new CloneStep();

	@Test
	void runsGitCloneIntoWorkspace() {
		RecordingCommandRunner runner = new RecordingCommandRunner();
		step.execute(context, runner);
		assertEquals(List.of("git", "clone", "https://github.com/adamw7/tools.git",
				workspace.resolve("tools").toString()), runner.commandAt(0));
		assertEquals(workspace, runner.invocations().get(0).workingDirectory());
	}

	@Test
	void failedCloneAbortsAdoption() {
		RecordingCommandRunner runner = new RecordingCommandRunner(
				command -> new CommandResult(command, 128, "fatal: repository not found"));
		assertThrows(AdoptionException.class, () -> step.execute(context, runner));
	}

	@Test
	void isNamedClone() {
		assertEquals("clone", step.name());
	}
}
