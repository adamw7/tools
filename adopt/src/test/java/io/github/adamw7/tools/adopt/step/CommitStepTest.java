package io.github.adamw7.tools.adopt.step;

import static io.github.adamw7.tools.test.ExpectedFailures.assertFailure;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.function.Function;

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
		assertEquals(List.of("git", "add", "-A"), runner.commandAt(1));
		assertEquals(List.of("git", "diff", "--cached", "--quiet"), runner.commandAt(2));
		assertEquals(List.of("git", "config", "--get", "user.name"), runner.commandAt(3));
		assertEquals(List.of("git", "config", "--get", "user.email"), runner.commandAt(4));
		assertEquals(List.of("git", "commit", "-m", "my message"), runner.commandAt(5));
	}

	@Test
	void suppliesAFallbackIdentityWhenGitHasNone() {
		RecordingCommandRunner runner = new RecordingCommandRunner(this::stagedChangesWithoutIdentity);
		new CommitStep("my message").execute(context, runner);
		assertEquals(List.of("git", "-c", "user.name=" + CommitStep.FALLBACK_NAME, "-c",
				"user.email=" + CommitStep.FALLBACK_EMAIL, "commit", "-m", "my message"), runner.commandAt(5));
	}

	@Test
	void leavesAConfiguredIdentityInForce() {
		RecordingCommandRunner runner = new RecordingCommandRunner(this::stagedChanges);
		new CommitStep("my message").execute(context, runner);
		assertEquals(List.of("git", "commit", "-m", "my message"), runner.commandAt(5));
	}

	@Test
	void skipsCommitWhenNothingIsStaged() {
		RecordingCommandRunner runner = new RecordingCommandRunner();
		new CommitStep("msg").execute(context, runner);
		assertEquals(3, runner.count());
		assertEquals(List.of("git", "diff", "--cached", "--quiet"), runner.commandAt(2));
	}

	/**
	 * The paths the adoption writes are asked about before anything is staged, since
	 * {@code git add -A} answers nothing about the ones it skipped.
	 */
	@Test
	void asksWhichOfTheAdoptionsOwnPathsTheCheckoutIgnores() {
		RecordingCommandRunner runner = new RecordingCommandRunner(this::stagedChanges);
		new CommitStep("msg").execute(context, runner);
		List<String> command = runner.commandAt(0);
		assertEquals(List.of("git", "ls-files", "--others", "--ignored", "--exclude-standard", "--"),
				command.subList(0, 6));
		assertEquals(AdoptionAssets.WRITTEN_PATHS, command.subList(6, command.size()));
	}

	/**
	 * A checkout that excludes {@code CLAUDE.md} would keep it in the working tree,
	 * where the verification passes, and out of the branch — leaving the guard the
	 * adoption wires in failing on a clean checkout of its own pull request.
	 */
	@Test
	void refusesToCommitAFileTheCheckoutIgnores() {
		RecordingCommandRunner runner = new RecordingCommandRunner(ignoring("CLAUDE.md"));
		assertFailure(AdoptionException.class, () -> new CommitStep("msg", "claude-md").execute(context, runner),
				"CLAUDE.md");
		assertEquals(1, runner.count(), "nothing may be staged once a path is known to be excluded");
	}

	/** Every excluded path is named, so one run says which patterns have to go. */
	@Test
	void namesEveryIgnoredPath() {
		RecordingCommandRunner runner = new RecordingCommandRunner(
				ignoring(AdoptionAssets.SETTINGS_FILE + "\n" + AdoptionAssets.SESSION_START_HOOK_FILE));
		assertFailure(AdoptionException.class, () -> new CommitStep("msg", "assets").execute(context, runner),
				AdoptionAssets.SETTINGS_FILE, AdoptionAssets.SESSION_START_HOOK_FILE);
	}

	/**
	 * The transcript merges standard error, so a git that warns puts a line in it
	 * that is no path of the adoption's and must not abort a perfectly good commit.
	 */
	@Test
	void ignoresTranscriptNoiseThatNamesNoPathOfTheAdoptions() {
		RecordingCommandRunner runner = new RecordingCommandRunner(
				ignoring("warning: unable to access '/etc/gitconfig'" + System.lineSeparator() + "build/output.txt"));
		new CommitStep("msg").execute(context, runner);
		assertEquals(List.of("git", "commit", "-m", "msg"), runner.commandAt(5));
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

	private Function<List<String>, CommandResult> ignoring(String paths) {
		return command -> command.contains("ls-files")
				? new CommandResult(command, 0, paths + System.lineSeparator())
				: stagedChanges(command);
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
