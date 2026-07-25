package io.github.adamw7.tools.adopt.step;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import io.github.adamw7.tools.adopt.AdoptionContext;
import io.github.adamw7.tools.adopt.AdoptionContexts;
import io.github.adamw7.tools.adopt.AdoptionException;
import io.github.adamw7.tools.adopt.command.CommandResult;
import io.github.adamw7.tools.adopt.command.RecordingCommandRunner;

class ClaudeInitStepTest {

	private void generateClaudeMd(AdoptionContext context) throws IOException {
		AdoptionContexts.write(context, "CLAUDE.md", "# CLAUDE.md");
	}

	/**
	 * Stands in for a CLI run that does what the step exists to make happen: writes
	 * the root CLAUDE.md. The file cannot be put there before the step runs, because
	 * a checkout that already carries one makes the step skip the CLI entirely.
	 */
	private RecordingCommandRunner generatingRunner(AdoptionContext context) {
		return new RecordingCommandRunner(command -> {
			writeRootClaudeMd(context);
			return new CommandResult(command, 0, "");
		});
	}

	private Path claudeDirMemory(AdoptionContext context) {
		return context.repositoryDirectory().resolve(".claude").resolve("CLAUDE.md");
	}

	private void writeClaudeDirMemory(AdoptionContext context) throws IOException {
		Path memory = claudeDirMemory(context);
		Files.createDirectories(memory.getParent());
		Files.writeString(memory, "# project memory");
	}

	/**
	 * The claude transcript carried by the original failure is the only useful
	 * diagnostic a failed run produces, so a restore that also fails must not throw
	 * over it — which is what a plain finally block would do.
	 */
	@Test
	void aFailedRestoreDoesNotReplaceTheOriginalFailure(@TempDir Path workspace) throws IOException {
		AdoptionContext context = AdoptionContexts.checkedOutIn(workspace);
		writeClaudeDirMemory(context);
		RecordingCommandRunner runner = new RecordingCommandRunner(command -> {
			blockRestore(context);
			return new CommandResult(command, 3, "claude: the underlying failure");
		});
		AdoptionException thrown = assertThrows(AdoptionException.class,
				() -> new ClaudeInitStep(List.of("claude")).execute(context, runner));
		assertTrue(thrown.getMessage().contains("the underlying failure"), thrown.getMessage());
		assertEquals(1, thrown.getSuppressed().length, "the failed restore must be attached, not thrown");
	}

	/**
	 * Puts a regular file where the {@code .claude} directory was, while the CLI is
	 * notionally running — that is, after the memory file has been moved aside and
	 * before it is put back — so the restore cannot succeed.
	 */
	private void blockRestore(AdoptionContext context) {
		Path claudeDir = context.repositoryDirectory().resolve(".claude");
		try {
			Files.delete(claudeDir);
			Files.writeString(claudeDir, "not a directory");
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}

	@Test
	void runsConfiguredClaudeCommandInCheckout(@TempDir Path workspace) throws IOException {
		AdoptionContext context = AdoptionContexts.checkedOutIn(workspace);
		RecordingCommandRunner runner = generatingRunner(context);
		new ClaudeInitStep(List.of("claude", "init")).execute(context, runner);
		assertEquals(List.of("claude", "init"), runner.commandAt(0));
		assertEquals(context.repositoryDirectory(), runner.invocations().get(0).workingDirectory());
	}

	@Test
	void defaultsToHeadlessInitCommand(@TempDir Path workspace) throws IOException {
		AdoptionContext context = AdoptionContexts.checkedOutIn(workspace);
		RecordingCommandRunner runner = generatingRunner(context);
		new ClaudeInitStep().execute(context, runner);
		assertEquals(ClaudeInitStep.DEFAULT_COMMAND, runner.commandAt(0));
	}

	/**
	 * The CLI's output is not reproducible, so re-running an adoption over a
	 * checkout that already has a CLAUDE.md would rewrite the file — discarding any
	 * edit made to it since — and produce a second commit with the same message. The
	 * rest of the pipeline skips work it has already done; this step must too.
	 */
	@Test
	void leavesAnAlreadyAdoptedCheckoutUntouched(@TempDir Path workspace) throws IOException {
		AdoptionContext context = AdoptionContexts.checkedOutIn(workspace);
		AdoptionContexts.write(context, "CLAUDE.md", "# CLAUDE.md\n\nHand-edited.");
		RecordingCommandRunner runner = new RecordingCommandRunner();
		new ClaudeInitStep().execute(context, runner);
		assertEquals(0, runner.count(), "claude must not be re-run over an existing CLAUDE.md");
		assertEquals("# CLAUDE.md\n\nHand-edited.",
				Files.readString(context.repositoryDirectory().resolve("CLAUDE.md")));
	}

	/**
	 * Skipping happens before the {@code .claude/CLAUDE.md} dance, so the memory
	 * file is never moved when there is no CLI run to steer.
	 */
	@Test
	void doesNotDisturbClaudeDirMemoryWhenSkipping(@TempDir Path workspace) throws IOException {
		AdoptionContext context = AdoptionContexts.checkedOutIn(workspace);
		generateClaudeMd(context);
		writeClaudeDirMemory(context);
		new ClaudeInitStep().execute(context, new RecordingCommandRunner());
		assertEquals("# project memory", Files.readString(claudeDirMemory(context)));
	}

	@Test
	void defaultCommandPreApprovesEditsSoHeadlessInitCanWriteTheFile() {
		assertEquals(List.of("claude", "-p", "/init", "--permission-mode", "acceptEdits"),
				ClaudeInitStep.DEFAULT_COMMAND);
	}

	@Test
	void failedInitAbortsAdoption(@TempDir Path workspace) throws IOException {
		AdoptionContext context = AdoptionContexts.checkedOutIn(workspace);
		RecordingCommandRunner runner = new RecordingCommandRunner(
				command -> new CommandResult(command, 1, "boom"));
		assertThrows(AdoptionException.class, () -> new ClaudeInitStep().execute(context, runner));
	}

	@Test
	void missingClaudeMdAbortsAdoption(@TempDir Path workspace) throws IOException {
		AdoptionContext context = AdoptionContexts.checkedOutIn(workspace);
		RecordingCommandRunner runner = new RecordingCommandRunner();
		assertThrows(AdoptionException.class, () -> new ClaudeInitStep().execute(context, runner));
	}

	@Test
	void movesExistingClaudeDirMemoryAsideSoHeadlessInitWritesRootFile(@TempDir Path workspace) throws IOException {
		AdoptionContext context = AdoptionContexts.checkedOutIn(workspace);
		writeClaudeDirMemory(context);
		AtomicBoolean memoryHiddenDuringInit = new AtomicBoolean();
		RecordingCommandRunner runner = new RecordingCommandRunner(command -> {
			memoryHiddenDuringInit.set(!Files.exists(claudeDirMemory(context)));
			writeRootClaudeMd(context);
			return new CommandResult(command, 0, "");
		});
		new ClaudeInitStep().execute(context, runner);
		assertTrue(memoryHiddenDuringInit.get(), ".claude/CLAUDE.md must be moved aside while /init runs");
		assertTrue(Files.isRegularFile(context.repositoryDirectory().resolve("CLAUDE.md")));
		assertEquals("# project memory", Files.readString(claudeDirMemory(context)));
	}

	@Test
	void restoresExistingClaudeDirMemoryWhenInitFails(@TempDir Path workspace) throws IOException {
		AdoptionContext context = AdoptionContexts.checkedOutIn(workspace);
		writeClaudeDirMemory(context);
		RecordingCommandRunner runner = new RecordingCommandRunner(
				command -> new CommandResult(command, 1, "boom"));
		assertThrows(AdoptionException.class, () -> new ClaudeInitStep().execute(context, runner));
		assertEquals("# project memory", Files.readString(claudeDirMemory(context)));
	}

	@Test
	void leavesRootClaudeMdUntouchedWhenNoClaudeDirMemoryExists(@TempDir Path workspace) throws IOException {
		AdoptionContext context = AdoptionContexts.checkedOutIn(workspace);
		new ClaudeInitStep().execute(context, generatingRunner(context));
		assertFalse(Files.exists(claudeDirMemory(context)));
	}

	private void writeRootClaudeMd(AdoptionContext context) {
		try {
			AdoptionContexts.write(context, "CLAUDE.md", "# CLAUDE.md");
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}
}
