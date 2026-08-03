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
		assertEquals(List.of("git", "config", "--get-all", "remote.origin.url"), runner.commandAt(0));
		assertEquals(List.of("git", "status", "--porcelain", "--untracked-files=all"), runner.commandAt(1));
		assertEquals(List.of("git", "fetch", "origin"), runner.commandAt(2));
		assertEquals(3, runner.count());
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
		assertEquals(3, runner.count());
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
		assertEquals(3, runner.count());
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
		assertEquals(List.of("git", "config", "--get-all", "remote.origin.url"), runner.commandAt(0));
		assertEquals(3, runner.count());
	}

	/**
	 * The remote is read from the configuration, not with {@code git remote get-url},
	 * which expands the caller's {@code url.<base>.insteadOf} rewrites: a rewrite onto
	 * a mirror or a proxy answers a host and path the checkout never recorded, and the
	 * reused checkout was refused as a different repository. Asking git for the raw
	 * configured value is what makes the comparison against the run's own URL possible
	 * at all.
	 */
	@Test
	void readsTheConfiguredOriginRatherThanTheRewrittenOne(@TempDir Path existingWorkspace) throws IOException {
		AdoptionContext existing = checkedOut(existingWorkspace);
		RecordingCommandRunner runner = new RecordingCommandRunner(command -> {
			if (command.contains("--get-all")) {
				return new CommandResult(command, 0, "https://github.com/adamw7/tools.git");
			}
			return new CommandResult(command, 0, command.contains("status") ? ""
					: "https://mirror.corp/github/adamw7/tools.git");
		});
		step.execute(existing, runner);
		assertEquals(List.of("git", "config", "--get-all", "remote.origin.url"), runner.commandAt(0));
		assertEquals(3, runner.count());
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
		RecordingCommandRunner runner = new RecordingCommandRunner(command -> {
			if (command.contains("fetch")) {
				return new CommandResult(command, 128, "fatal: could not read from remote repository");
			}
			return new CommandResult(command, 0, command.contains("status") ? ""
					: "https://github.com/adamw7/tools.git");
		});
		assertThrows(AdoptionException.class, () -> step.execute(existing, runner));
	}

	@Test
	void failedCloneAbortsAdoption() {
		RecordingCommandRunner runner = RecordingCommandRunner.failing(128, "fatal: repository not found");
		assertThrows(AdoptionException.class, () -> step.execute(context, runner));
	}

	/**
	 * {@link CommitStep} stages the whole tree, so a contributor's work in progress in
	 * a reused checkout would be committed to the adoption's branch and pushed for
	 * review as part of adopting Claude Code.
	 */
	@Test
	void refusesReusedCheckoutCarryingSomebodyElsesUncommittedWork(@TempDir Path existingWorkspace)
			throws IOException {
		AdoptionContext existing = checkedOut(existingWorkspace);
		RecordingCommandRunner runner = answering("https://github.com/adamw7/tools.git",
				" M src/main/java/Service.java" + System.lineSeparator() + "?? notes.txt");

		AdoptionException failure = assertThrows(AdoptionException.class, () -> step.execute(existing, runner));

		assertTrue(failure.getMessage().contains("src/main/java/Service.java"), failure.getMessage());
		assertTrue(failure.getMessage().contains("notes.txt"), failure.getMessage());
		assertEquals(2, runner.count(), "a checkout with unrelated work may not even be fetched into");
	}

	/**
	 * An adoption that failed between writing a file and committing it leaves exactly
	 * the adoption's own paths behind, and re-running it is the case most worth
	 * resuming — it has already paid for a {@code claude init}.
	 */
	@Test
	void resumesReusedCheckoutCarryingOnlyTheAdoptionsOwnChanges(@TempDir Path existingWorkspace)
			throws IOException {
		AdoptionContext existing = checkedOut(existingWorkspace);
		RecordingCommandRunner runner = answering("https://github.com/adamw7/tools.git",
				"?? CLAUDE.md" + System.lineSeparator() + " M pom.xml" + System.lineSeparator()
						+ "?? .claude/settings.json");

		step.execute(existing, runner);

		assertEquals(3, runner.count());
	}

	/**
	 * The status is asked with {@code --untracked-files=all}, because git otherwise
	 * collapses a wholly-untracked directory into one entry naming the directory. The
	 * adoption creates {@code .claude/} in a repository that by definition had none,
	 * so the default reported its own work as the single path {@code .claude/} —
	 * which is not one of the files it writes, and the resume it is entitled to was
	 * refused.
	 */
	@Test
	void asksForEveryUntrackedFileRatherThanTheDirectoriesTheyAreIn(@TempDir Path existingWorkspace)
			throws IOException {
		AdoptionContext existing = checkedOut(existingWorkspace);
		RecordingCommandRunner runner = origin("https://github.com/adamw7/tools.git");

		step.execute(existing, runner);

		assertTrue(runner.commandAt(1).contains("--untracked-files=all"), runner.commandAt(1).toString());
	}

	/**
	 * Every file the adoption writes into a directory the repository did not have, as
	 * {@code --untracked-files=all} names them. This is the state a run that stopped
	 * between {@link AssetsStep} and its commit leaves behind.
	 */
	@Test
	void resumesReusedCheckoutWhoseAssetDirectoriesAreEntirelyNew(@TempDir Path existingWorkspace)
			throws IOException {
		AdoptionContext existing = checkedOut(existingWorkspace);
		RecordingCommandRunner runner = answering("https://github.com/adamw7/tools.git", String.join(
				System.lineSeparator(),
				"?? .claude/hooks/session-start.sh",
				"?? .claude/settings.json",
				"?? .github/workflows/claude.yml",
				"?? .mcp.json",
				"?? AGENTS.md",
				"?? CLAUDE.md"));

		step.execute(existing, runner);

		assertEquals(3, runner.count());
	}

	/** The fallback guard's two files land under a {@code .github/} a project may equally not have had. */
	@Test
	void resumesReusedCheckoutCarryingTheFallbackGuardsOwnFiles(@TempDir Path existingWorkspace)
			throws IOException {
		AdoptionContext existing = checkedOut(existingWorkspace);
		RecordingCommandRunner runner = answering("https://github.com/adamw7/tools.git", String.join(
				System.lineSeparator(),
				"?? .github/claude-md-guard.sh",
				"?? .github/workflows/claude-md-guard.yml"));

		step.execute(existing, runner);

		assertEquals(3, runner.count());
	}

	/** Both Gradle build files the guard may be appended to are the adoption's own edits. */
	@Test
	void resumesReusedCheckoutCarryingAnEditedGradleBuildFile(@TempDir Path existingWorkspace) throws IOException {
		AdoptionContext existing = checkedOut(existingWorkspace);
		RecordingCommandRunner runner = answering("https://github.com/adamw7/tools.git",
				" M build.gradle" + System.lineSeparator() + " M build.gradle.kts");

		step.execute(existing, runner);

		assertEquals(3, runner.count());
	}

	/**
	 * A collapsed directory entry names no file the adoption writes, so it is refused
	 * rather than guessed at: were the flag ever dropped, the resume would fail loudly
	 * instead of sweeping whatever else that directory holds into the adoption's
	 * commit.
	 */
	@Test
	void refusesADirectoryEntryThatNamesNoFileTheAdoptionWrites(@TempDir Path existingWorkspace)
			throws IOException {
		AdoptionContext existing = checkedOut(existingWorkspace);
		RecordingCommandRunner runner = answering("https://github.com/adamw7/tools.git", "?? .claude/");

		AdoptionException failure = assertThrows(AdoptionException.class, () -> step.execute(existing, runner));

		assertTrue(failure.getMessage().contains(".claude/"), failure.getMessage());
	}

	/**
	 * One unrelated file among the adoption's own still stops the run, so the check
	 * is not weakened into "some of this is mine".
	 */
	@Test
	void refusesReusedCheckoutMixingTheAdoptionsChangesWithSomebodyElses(@TempDir Path existingWorkspace)
			throws IOException {
		AdoptionContext existing = checkedOut(existingWorkspace);
		RecordingCommandRunner runner = answering("https://github.com/adamw7/tools.git",
				"?? CLAUDE.md" + System.lineSeparator() + "?? .claude/settings.json" + System.lineSeparator()
						+ "?? .claude/scratch.md");

		AdoptionException failure = assertThrows(AdoptionException.class, () -> step.execute(existing, runner));

		assertTrue(failure.getMessage().contains(".claude/scratch.md"), failure.getMessage());
		assertFalse(failure.getMessage().contains("settings.json"), failure.getMessage());
	}

	/** A clean checkout reports nothing, and the blank transcript names no changed path. */
	@Test
	void readsAnEmptyStatusAsACleanCheckout(@TempDir Path existingWorkspace) throws IOException {
		AdoptionContext existing = checkedOut(existingWorkspace);
		RecordingCommandRunner runner = answering("https://github.com/adamw7/tools.git",
				System.lineSeparator() + "   " + System.lineSeparator());

		step.execute(existing, runner);

		assertEquals(3, runner.count());
	}

	/** Staged and unstaged forms of the adoption's own files carry different status letters. */
	@Test
	void readsEveryStatusLetterTheAdoptionsOwnFilesArriveWith(@TempDir Path existingWorkspace) throws IOException {
		AdoptionContext existing = checkedOut(existingWorkspace);
		RecordingCommandRunner runner = answering("https://github.com/adamw7/tools.git", String.join(
				System.lineSeparator(),
				"A  CLAUDE.md",
				"M  pom.xml",
				" M AGENTS.md",
				"MM .mcp.json",
				"?? .claude/settings.json"));

		step.execute(existing, runner);

		assertEquals(3, runner.count());
	}

	/** A path with a space is quoted by git, and a rename names the path it ends at. */
	@Test
	void readsQuotedAndRenamedPathsAsGitReportsThem(@TempDir Path existingWorkspace) throws IOException {
		AdoptionContext existing = checkedOut(existingWorkspace);
		RecordingCommandRunner runner = answering("https://github.com/adamw7/tools.git",
				"R  old/name.txt -> \"my notes.txt\"");

		AdoptionException failure = assertThrows(AdoptionException.class, () -> step.execute(existing, runner));

		assertTrue(failure.getMessage().contains("my notes.txt"), failure.getMessage());
		assertFalse(failure.getMessage().contains("->"), failure.getMessage());
	}

	/**
	 * A rename changes the path it starts at as much as the one it ends at. Reading
	 * only the destination waved the whole entry through whenever that destination
	 * was a path the adoption writes, so a contributor's staged
	 * {@code git mv docs/CLAUDE.md CLAUDE.md} was swept into the adoption's commit
	 * and pushed — which is what this guard exists to prevent.
	 */
	@Test
	void refusesRenameOntoAnAdoptionPathBecauseItsSourceIsUnrelated(@TempDir Path existingWorkspace)
			throws IOException {
		AdoptionContext existing = checkedOut(existingWorkspace);
		RecordingCommandRunner runner = answering("https://github.com/adamw7/tools.git",
				"R  docs/CLAUDE.md -> CLAUDE.md");

		AdoptionException failure = assertThrows(AdoptionException.class, () -> step.execute(existing, runner));

		assertTrue(failure.getMessage().contains("docs/CLAUDE.md"), failure.getMessage());
	}

	/** A copy leaves its source in place but is reported the same way, so it is read the same way. */
	@Test
	void refusesCopyOntoAnAdoptionPathBecauseItsSourceIsUnrelated(@TempDir Path existingWorkspace)
			throws IOException {
		AdoptionContext existing = checkedOut(existingWorkspace);
		RecordingCommandRunner runner = answering("https://github.com/adamw7/tools.git",
				"C  Feature.java -> pom.xml");

		AdoptionException failure = assertThrows(AdoptionException.class, () -> step.execute(existing, runner));

		assertTrue(failure.getMessage().contains("Feature.java"), failure.getMessage());
	}

	/** Both sides being the adoption's own is a resume, not a contributor's work in progress. */
	@Test
	void resumesWhenBothSidesOfARenameAreTheAdoptionsOwn(@TempDir Path existingWorkspace) throws IOException {
		AdoptionContext existing = checkedOut(existingWorkspace);
		RecordingCommandRunner runner = answering("https://github.com/adamw7/tools.git",
				"R  AGENTS.md -> CLAUDE.md");

		step.execute(existing, runner);

		assertEquals(3, runner.count());
	}

	/** A status that cannot be read cannot show the checkout to be free of other work. */
	@Test
	void refusesReusedCheckoutWhoseStatusCannotBeRead(@TempDir Path existingWorkspace) throws IOException {
		AdoptionContext existing = checkedOut(existingWorkspace);
		RecordingCommandRunner runner = new RecordingCommandRunner(command -> command.contains("status")
				? new CommandResult(command, 128, "fatal: not a git repository")
				: new CommandResult(command, 0, "https://github.com/adamw7/tools.git"));

		AdoptionException failure = assertThrows(AdoptionException.class, () -> step.execute(existing, runner));

		assertTrue(failure.getMessage().contains("status could not be read"), failure.getMessage());
	}

	/**
	 * The runner merges standard error into the transcript, so a git that warns puts
	 * that line among the status entries. Read as an entry it named a path the
	 * adoption does not write, and the resume this step promises was refused for
	 * changes the checkout never had.
	 */
	@Test
	void resumesReusedCheckoutWhoseStatusWarnedOnStandardError(@TempDir Path existingWorkspace) throws IOException {
		AdoptionContext existing = checkedOut(existingWorkspace);
		RecordingCommandRunner runner = answering("https://github.com/adamw7/tools.git", String.join(
				System.lineSeparator(),
				"warning: unable to access '/etc/gitconfig': Permission denied",
				"?? CLAUDE.md"));

		step.execute(existing, runner);

		assertEquals(3, runner.count());
	}

	/** A warning is skipped, not read as a change — but a real entry beside it still stops the run. */
	@Test
	void refusesReusedCheckoutWhoseWarnedStatusAlsoNamesSomebodyElsesWork(@TempDir Path existingWorkspace)
			throws IOException {
		AdoptionContext existing = checkedOut(existingWorkspace);
		RecordingCommandRunner runner = answering("https://github.com/adamw7/tools.git", String.join(
				System.lineSeparator(),
				"warning: could not open directory 'vendor/': Permission denied",
				" M src/Main.java"));

		AdoptionException failure = assertThrows(AdoptionException.class, () -> step.execute(existing, runner));

		assertTrue(failure.getMessage().contains("src/Main.java"), failure.getMessage());
		assertFalse(failure.getMessage().contains("warning"), failure.getMessage());
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

	/**
	 * A runner answering the origin query with the URL git would report, and every
	 * other command — the status check, the fetch — with a clean success. Only the
	 * origin query is answered with the URL, because a status answered with one would
	 * read as a changed file by that name.
	 */
	private RecordingCommandRunner origin(String url) {
		return answering(url, "");
	}

	/** A runner answering the origin query and the status check, in that order. */
	private RecordingCommandRunner answering(String url, String status) {
		return new RecordingCommandRunner(command -> {
			if (command.contains("--get-all")) {
				return new CommandResult(command, 0, url + System.lineSeparator());
			}
			return new CommandResult(command, 0, command.contains("status") ? status : "");
		});
	}
}
