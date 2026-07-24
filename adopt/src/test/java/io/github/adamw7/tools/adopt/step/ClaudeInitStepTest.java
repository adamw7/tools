package io.github.adamw7.tools.adopt.step;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import io.github.adamw7.tools.adopt.AdoptionContext;
import io.github.adamw7.tools.adopt.AdoptionException;
import io.github.adamw7.tools.adopt.command.CommandResult;
import io.github.adamw7.tools.adopt.command.RecordingCommandRunner;

class ClaudeInitStepTest {

	private AdoptionContext context(Path workspace) throws IOException {
		AdoptionContext context = new AdoptionContext("https://github.com/adamw7/tools.git", workspace);
		Files.createDirectories(context.repositoryDirectory());
		return context;
	}

	private void generateClaudeMd(AdoptionContext context) throws IOException {
		Files.writeString(context.repositoryDirectory().resolve("CLAUDE.md"), "# CLAUDE.md");
	}

	@Test
	void runsConfiguredClaudeCommandInCheckout(@TempDir Path workspace) throws IOException {
		AdoptionContext context = context(workspace);
		generateClaudeMd(context);
		RecordingCommandRunner runner = new RecordingCommandRunner();
		new ClaudeInitStep(List.of("claude", "init")).execute(context, runner);
		assertEquals(List.of("claude", "init"), runner.commandAt(0));
		assertEquals(context.repositoryDirectory(), runner.invocations().get(0).workingDirectory());
	}

	@Test
	void defaultsToHeadlessInitCommand(@TempDir Path workspace) throws IOException {
		AdoptionContext context = context(workspace);
		generateClaudeMd(context);
		RecordingCommandRunner runner = new RecordingCommandRunner();
		new ClaudeInitStep().execute(context, runner);
		assertEquals(ClaudeInitStep.DEFAULT_COMMAND, runner.commandAt(0));
	}

	@Test
	void defaultCommandPreApprovesEditsSoHeadlessInitCanWriteTheFile() {
		assertEquals(List.of("claude", "-p", "/init", "--permission-mode", "acceptEdits"),
				ClaudeInitStep.DEFAULT_COMMAND);
	}

	@Test
	void failedInitAbortsAdoption(@TempDir Path workspace) throws IOException {
		AdoptionContext context = context(workspace);
		RecordingCommandRunner runner = new RecordingCommandRunner(
				command -> new CommandResult(command, 1, "boom"));
		assertThrows(AdoptionException.class, () -> new ClaudeInitStep().execute(context, runner));
	}

	@Test
	void missingClaudeMdAbortsAdoption(@TempDir Path workspace) throws IOException {
		AdoptionContext context = context(workspace);
		RecordingCommandRunner runner = new RecordingCommandRunner();
		assertThrows(AdoptionException.class, () -> new ClaudeInitStep().execute(context, runner));
	}
}
