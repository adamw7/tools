package io.github.adamw7.tools.adopt.step;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

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

	/**
	 * The checkout is reused rather than re-cloned, but only once its {@code origin}
	 * has confirmed it to be the repository under adoption — and it is refreshed, so
	 * the branch step reads remote-tracking refs that are up to date.
	 */
	@Test
	void reusesCheckoutOfTheSameRepositoryInsteadOfCloning(@TempDir Path existingWorkspace) throws IOException {
		AdoptionContext existing = checkedOut(existingWorkspace);
		RecordingCommandRunner runner = origin("https://github.com/adamw7/tools.git");
		step.execute(existing, runner);
		assertEquals(List.of("git", "remote", "get-url", "origin"), runner.commandAt(0));
		assertEquals(List.of("git", "fetch", "origin"), runner.commandAt(1));
		assertEquals(2, runner.count());
	}

	/**
	 * The checkout an earlier run left behind was cloned from whichever of the
	 * repository's URLs that run was given, so a reuse that only accepted the URL
	 * back verbatim would re-clone over a checkout that was already the right one.
	 */
	@Test
	void reusesCheckoutClonedFromAnotherFormOfTheSameUrl(@TempDir Path existingWorkspace) throws IOException {
		AdoptionContext existing = checkedOut(existingWorkspace);
		RecordingCommandRunner runner = origin("git@github.com:adamw7/Tools");
		step.execute(existing, runner);
		assertEquals(2, runner.count());
	}

	/**
	 * Two repositories of one name claim one checkout directory. Within a run
	 * {@code Checkouts} refuses the second; across two runs into the same
	 * {@code --workspace} only this check stands between the operator and an
	 * adoption that branches, commits, and pushes the wrong repository.
	 */
	@Test
	void refusesCheckoutOfADifferentRepository(@TempDir Path existingWorkspace) throws IOException {
		AdoptionContext existing = checkedOut(existingWorkspace);
		RecordingCommandRunner runner = origin("https://github.com/someone-else/tools.git");
		AdoptionException failure = assertThrows(AdoptionException.class, () -> step.execute(existing, runner));
		assertTrue(failure.getMessage().contains("someone-else/tools"), failure.getMessage());
		assertTrue(failure.getMessage().contains("adamw7/tools"), failure.getMessage());
		assertEquals(1, runner.count(), "nothing may be done to a checkout of another repository");
	}

	/** The origin of a checkout that fails the check is reported without its credentials. */
	@Test
	void masksCredentialsOfTheRefusedCheckoutsOrigin(@TempDir Path existingWorkspace) throws IOException {
		AdoptionContext existing = checkedOut(existingWorkspace);
		RecordingCommandRunner runner = origin("https://x-access-token:SECRET@github.com/someone-else/tools.git");
		AdoptionException failure = assertThrows(AdoptionException.class, () -> step.execute(existing, runner));
		assertFalse(failure.getMessage().contains("SECRET"), failure.getMessage());
	}

	/**
	 * The runner merges standard error into the transcript, so a git that warns puts
	 * that line in beside the URL. Reading the whole transcript as the origin refused
	 * a checkout that was the right one and named the warning as the repository it
	 * supposedly held.
	 */
	@Test
	void reusesCheckoutWhoseOriginIsReportedAlongsideAWarning(@TempDir Path existingWorkspace) throws IOException {
		AdoptionContext existing = checkedOut(existingWorkspace);
		RecordingCommandRunner runner = origin("warning: unable to access '/etc/gitconfig': Permission denied"
				+ System.lineSeparator() + "https://github.com/adamw7/tools.git");
		step.execute(existing, runner);
		assertEquals(2, runner.count());
	}

	/** Noise on its own still names no repository, so the checkout is refused. */
	@Test
	void refusesCheckoutWhoseOriginIsOnlyNoise(@TempDir Path existingWorkspace) throws IOException {
		AdoptionContext existing = checkedOut(existingWorkspace);
		RecordingCommandRunner runner = origin("warning: unable to access '/etc/gitconfig': Permission denied");
		assertThrows(AdoptionException.class, () -> step.execute(existing, runner));
	}

	/**
	 * A linked worktree records its {@code .git} as a file rather than a directory.
	 * Insisting on the directory ran {@code git clone} into a directory that was not
	 * empty, which git refuses — aborting on a checkout the step was meant to reuse.
	 */
	@Test
	void reusesCheckoutWhoseGitIsAFile(@TempDir Path existingWorkspace) throws IOException {
		AdoptionContext existing = new AdoptionContext("https://github.com/adamw7/tools.git", existingWorkspace);
		Files.createDirectories(existing.repositoryDirectory());
		Files.writeString(existing.repositoryDirectory().resolve(".git"), "gitdir: /elsewhere/.git/worktrees/tools\n");
		RecordingCommandRunner runner = origin("https://github.com/adamw7/tools.git");
		step.execute(existing, runner);
		assertEquals(List.of("git", "remote", "get-url", "origin"), runner.commandAt(0));
		assertEquals(2, runner.count());
	}

	@Test
	void refusesCheckoutWithNoOriginRemote(@TempDir Path existingWorkspace) throws IOException {
		AdoptionContext existing = checkedOut(existingWorkspace);
		RecordingCommandRunner runner = RecordingCommandRunner.failing(2, "error: No such remote 'origin'");
		AdoptionException failure = assertThrows(AdoptionException.class, () -> step.execute(existing, runner));
		assertTrue(failure.getMessage().contains("no 'origin' remote"), failure.getMessage());
	}

	@Test
	void failedFetchAbortsAdoption(@TempDir Path existingWorkspace) throws IOException {
		AdoptionContext existing = checkedOut(existingWorkspace);
		RecordingCommandRunner runner = new RecordingCommandRunner(command -> command.contains("fetch")
				? new CommandResult(command, 128, "fatal: could not read from remote repository")
				: new CommandResult(command, 0, "https://github.com/adamw7/tools.git"));
		assertThrows(AdoptionException.class, () -> step.execute(existing, runner));
	}

	@Test
	void failedCloneAbortsAdoption() {
		RecordingCommandRunner runner = RecordingCommandRunner.failing(128, "fatal: repository not found");
		assertThrows(AdoptionException.class, () -> step.execute(context, runner));
	}

	@Test
	void isNamedClone() {
		assertEquals("clone", step.name());
	}

	/** A checkout of the repository under test, as an earlier run would have left it. */
	private AdoptionContext checkedOut(Path existingWorkspace) throws IOException {
		AdoptionContext existing = new AdoptionContext("https://github.com/adamw7/tools.git", existingWorkspace);
		Files.createDirectories(existing.repositoryDirectory().resolve(".git"));
		return existing;
	}

	/** A runner answering every command with the URL git would report as the checkout's origin. */
	private RecordingCommandRunner origin(String url) {
		return new RecordingCommandRunner(command -> new CommandResult(command, 0, url + System.lineSeparator()));
	}
}
