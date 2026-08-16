package io.github.adamw7.tools.adopt.step;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import io.github.adamw7.tools.adopt.AdoptionContext;
import io.github.adamw7.tools.adopt.AdoptionException;
import io.github.adamw7.tools.adopt.AdoptionReport;
import io.github.adamw7.tools.adopt.Redaction;
import io.github.adamw7.tools.adopt.command.CommandResult;
import io.github.adamw7.tools.adopt.command.CommandRunner;

/**
 * Clones the target repository into the workspace with {@code git clone}, so the
 * remaining steps have a working checkout to operate on. The step is idempotent:
 * a checkout that already exists (its {@code .git} directory is present) is
 * reused rather than re-cloned, so re-running the adoption against the same
 * workspace does not abort on an "already exists" clone failure.
 *
 * <p>A reused checkout is confirmed to be the repository under adoption before
 * anything is done to it, by comparing the repository its {@code origin} names
 * with the one the run was given. A checkout directory is named after the
 * repository alone, so two repositories of the same name — {@code alice/tools}
 * and {@code bob/tools}, or one repository and its fork — claim one directory
 * under a named {@code --workspace}. {@link io.github.adamw7.tools.adopt.Checkouts}
 * catches that collision within a single run, but two runs against the same
 * workspace never meet; without this check the second adoption would silently
 * branch, commit, and push the first repository's working tree, and report the
 * first repository's pull request as the second's.
 *
 * <p>A reused checkout must also be free of uncommitted work that is not the
 * adoption's own, since {@link CommitStep} stages the whole tree and would push a
 * contributor's work in progress as part of the adoption.
 *
 * <p>The checkout is then refreshed with {@code git fetch}, because every later
 * decision about the feature branch is taken against the remote-tracking refs a
 * stale checkout has out of date.
 *
 * <p>A freshly cloned checkout records the credential-free form of the URL as its
 * {@code origin}, so the token an adoption is driven with does not outlive the run
 * in {@code .git/config} — see {@link #forgetCredentials}.
 */
public class CloneStep extends AbstractCommandStep {

	private static final Logger log = LogManager.getLogger(CloneStep.class);

	/** The two status letters and the space before the path in {@code git status --porcelain}. */
	private static final int STATUS_PREFIX = 3;

	/**
	 * Makes {@code git status} name every untracked file rather than collapsing a
	 * wholly-untracked directory into a single entry. Without it a checkout whose
	 * {@code .claude/} the adoption had just created was reported as that one path,
	 * which is not among {@link AdoptionAssets#WRITTEN_PATHS} — so a resumed adoption
	 * was refused for changes that were its own. That is the common case: a repository
	 * being adopted has no {@code .claude/} to begin with.
	 */
	private static final String UNTRACKED_FILES_ALL = "--untracked-files=all";

	/** What {@code git status --porcelain} puts between a rename's old and new path. */
	private static final String RENAME_ARROW = " -> ";

	/**
	 * One entry of {@code git status --porcelain}: the index and work-tree status
	 * letters — the alphabet git documents for the format — then the space before the
	 * path.
	 *
	 * <p>The transcript merges standard error into standard output, so it is not
	 * reliably status entries and nothing else: a git that warns puts that line in
	 * there too. Reading every line as an entry took the warning's fourth character
	 * onwards for a path and refused the resume this step promises. At least one of
	 * the two letters therefore has to say something — git never reports a path whose
	 * index and work tree are both unchanged — because accepting two spaces let
	 * through any line indented by three of them.
	 */
	private static final Pattern STATUS_ENTRY = Pattern.compile("^(?! {2})[ MTADRCU?!]{2} .+");

	@Override
	public String name() {
		return "clone";
	}

	/**
	 * Records the checkout before doing anything to it, so a run that fails part-way
	 * still says where the working tree it left behind is. Nothing else answers the
	 * question: a caller that named no workspace was given a temporary one, and a dry
	 * run leaves that directory as the only thing there is to read.
	 */
	@Override
	public void execute(AdoptionContext context, CommandRunner runner, AdoptionReport report) {
		report.recordCheckout(context.repositoryDirectory().toAbsolutePath().toString());
		execute(context, runner);
	}

	@Override
	public void execute(AdoptionContext context, CommandRunner runner) {
		if (alreadyCloned(context)) {
			reuse(context, runner);
		} else {
			cloneAfresh(context, runner);
		}
	}

	private void cloneAfresh(AdoptionContext context, CommandRunner runner) {
		log.info("Cloning {} into {}", context.displayUrl(), context.repositoryDirectory());
		List<String> command = List.of("git", "clone", context.repositoryUrl(),
				context.repositoryDirectory().toString());
		runOrFail(runner, context.workspace(), command);
		forgetCredentials(context, runner);
	}

	/**
	 * Rewrites the {@code origin} git just recorded to the credential-free form of the
	 * same URL, because {@code git clone} writes the URL it was handed into
	 * {@code .git/config} verbatim — so an adoption driven by CI, whose URL carries
	 * {@code x-access-token:TOKEN}, left that token in plaintext in the workspace, for
	 * as long as the workspace survives. Masking it in the logs and the report keeps it
	 * out of what the run <em>says</em>; nothing but this keeps it out of what the run
	 * <em>leaves behind</em>.
	 *
	 * <p>{@link PushStep} is unaffected: it supplies the credentialled URL to the one
	 * command that needs it, rather than reading it back from the checkout.
	 *
	 * <p>Only the remote this step just wrote is rewritten, and only when the URL
	 * carried credentials at all. A reused checkout's {@code origin} is left exactly as
	 * its owner configured it — it is not the adoption's to edit, and the operator may
	 * well push through it themselves afterwards.
	 */
	private void forgetCredentials(AdoptionContext context, CommandRunner runner) {
		if (context.checkoutUrl().equals(context.repositoryUrl())) {
			return;
		}
		log.info("Recording {} as the checkout's {}, so the clone credentials are not left in .git/config",
				context.displayUrl(), AdoptionContext.REMOTE);
		runOrFail(runner, context.repositoryDirectory(),
				List.of("git", "remote", "set-url", AdoptionContext.REMOTE, context.checkoutUrl()));
	}

	private void reuse(AdoptionContext context, CommandRunner runner) {
		requireCheckoutOfTheSameRepository(context, runner);
		requireNoUnrelatedChanges(context, runner);
		log.info("{} already contains a checkout of {}; skipping clone", context.repositoryDirectory(),
				context.displayUrl());
		refresh(context, runner);
	}

	/**
	 * Refuses a reused checkout carrying uncommitted work that is not the adoption's
	 * own. {@link CommitStep} stages the whole tree with {@code git add -A}, so
	 * whatever a contributor had in progress in that checkout would be swept into the
	 * adoption's commit, pushed to the feature branch, and offered for review as part
	 * of adopting Claude Code.
	 *
	 * <p>Only unrelated paths stop the run. An adoption that failed between writing a
	 * file and committing it leaves exactly the paths {@link AdoptionAssets} names, so
	 * re-running against the same workspace still resumes — which is the case worth
	 * resuming, the one that has already paid for a {@code claude init}.
	 */
	private void requireNoUnrelatedChanges(AdoptionContext context, CommandRunner runner) {
		List<String> unrelated = unrelatedChanges(context, runner);
		if (!unrelated.isEmpty()) {
			throw new AdoptionException(context.repositoryDirectory() + " has uncommitted changes that are not the"
					+ " adoption's: " + String.join(", ", unrelated) + ". The adoption commits everything in the"
					+ " checkout, so these would be pushed to " + context.branchName()
					+ " too. Commit or stash them, or adopt into a fresh --workspace.");
		}
	}

	/**
	 * The changed paths the adoption does not own. A rename is reported as
	 * {@code old -> new} and names two of them, and a path carrying a space or a quote
	 * is reported quoted, so every entry is reduced to the paths git would act on
	 * before they are compared.
	 */
	private List<String> unrelatedChanges(AdoptionContext context, CommandRunner runner) {
		String transcript = askGit(context, runner, List.of("git", "status", "--porcelain", UNTRACKED_FILES_ALL),
				"is a checkout whose status could not be read, so it cannot be shown to be free of uncommitted work");
		return transcript.lines()
				.filter(line -> STATUS_ENTRY.matcher(line).matches())
				.flatMap(this::changedPaths)
				.filter(path -> !AdoptionAssets.WRITTEN_PATHS.contains(path))
				.toList();
	}

	/**
	 * The paths one entry changes. A rename or a copy is reported as
	 * {@code old -> new} and changes <em>both</em> sides, so both are asked about:
	 * reading only the destination waved the whole entry through whenever that
	 * destination happened to be a path the adoption writes, so a contributor's staged
	 * {@code git mv docs/CLAUDE.md CLAUDE.md} was swept into the adoption's commit.
	 */
	private Stream<String> changedPaths(String statusLine) {
		String path = statusLine.substring(STATUS_PREFIX);
		int rename = path.lastIndexOf(RENAME_ARROW);
		if (rename < 0) {
			return Stream.of(unquoted(path));
		}
		return Stream.of(unquoted(path.substring(0, rename)),
				unquoted(path.substring(rename + RENAME_ARROW.length())));
	}

	private String unquoted(String path) {
		String stripped = path.strip();
		return stripped.length() > 1 && stripped.startsWith("\"") && stripped.endsWith("\"")
				? stripped.substring(1, stripped.length() - 1)
				: stripped;
	}

	/**
	 * The failure names both repositories and the directory they disagree about,
	 * since that is what the operator has to act on — and it says what to do, because
	 * neither answer is obvious: adopt into a workspace of its own, or clear the
	 * directory the other repository left behind.
	 */
	private void requireCheckoutOfTheSameRepository(AdoptionContext context, CommandRunner runner) {
		String transcript = originTranscript(context, runner);
		if (!namesThisRepository(context, transcript)) {
			throw new AdoptionException(context.repositoryDirectory() + " already holds a checkout of "
					+ Redaction.of(transcript.strip()) + ", not of " + context.displayUrl()
					+ ". Adopt this repository into its own --workspace, or remove that directory first.");
		}
	}

	/**
	 * The transcript merges standard error into standard output, so the {@code origin}
	 * URL is not reliably the whole of it and reading it as one URL failed a checkout
	 * that was the right one all along. Every line is asked in turn, which keeps the
	 * conservative reading {@link AdoptionContext#isSameRepository} is built on: noise
	 * names no repository, so a transcript carrying nothing else still answers no.
	 */
	private boolean namesThisRepository(AdoptionContext context, String transcript) {
		return transcript.lines().map(String::strip).anyMatch(context::isSameRepository);
	}

	/**
	 * A directory carrying a {@code .git} but no {@code origin} is reported rather
	 * than adopted: it cannot be shown to be the repository under adoption, and
	 * {@link PushStep} would have nowhere to publish the branch to anyway.
	 *
	 * <p>The remote is read from the configuration rather than with {@code git remote
	 * get-url}, which expands the {@code url.<base>.insteadOf} rewrites the caller's
	 * git may configure and so answers a URL the checkout never recorded. A rewrite
	 * onto a mirror names a different host and path, so the reused checkout was
	 * refused as a different repository in exactly the environments that configure
	 * one. Only the raw configured value can be compared with the URL the run was
	 * asked to adopt.
	 */
	private String originTranscript(AdoptionContext context, CommandRunner runner) {
		return askGit(context, runner,
				List.of("git", "config", "--get-all", "remote." + AdoptionContext.REMOTE + ".url"),
				"is a checkout with no '" + AdoptionContext.REMOTE + "' remote, so it can be neither confirmed to be "
						+ context.displayUrl() + " nor pushed to it");
	}

	/**
	 * Puts a question to git about the checkout being reused, answering with the
	 * transcript. A query that could not be <em>run</em> leaves a checkout this step
	 * cannot vouch for rather than an adoption that may go on, so it stops the run —
	 * and stops it saying what {@link #runOrFail} does not: which directory, what
	 * could not be established about it, and only then git's own words.
	 *
	 * <p>Both questions are asked through here because both have the same failure to
	 * avoid. Each reads its answer out of the transcript, and a transcript that is
	 * empty because the command never ran is indistinguishable from one that is empty
	 * because the answer was "nothing" — a checkout with no {@code origin} read as one
	 * whose {@code origin} matched, and an unreadable status as a clean working tree.
	 *
	 * @param unanswered what a failed query leaves unknown, reported after the
	 *                   checkout directory and before the redacted transcript
	 */
	private String askGit(AdoptionContext context, CommandRunner runner, List<String> command, String unanswered) {
		CommandResult result = runner.run(context.repositoryDirectory(), command);
		if (!result.succeeded()) {
			throw new AdoptionException(context.repositoryDirectory() + " " + unanswered + ": "
					+ result.redactedOutput().strip());
		}
		return result.output();
	}

	/**
	 * Brings the reused checkout's remote-tracking refs up to date, so
	 * {@link BranchStep} sees a feature branch an earlier adoption published and
	 * resumes from its tip. A checkout that predates that push carries no
	 * {@code origin/<branch>} ref, which would restart the branch at the default
	 * branch and leave {@link PushStep} refused as a non-fast-forward.
	 */
	private void refresh(AdoptionContext context, CommandRunner runner) {
		log.info("Fetching {} in {}", AdoptionContext.REMOTE, context.repositoryDirectory());
		runOrFail(runner, context.repositoryDirectory(), List.of("git", "fetch", AdoptionContext.REMOTE));
	}

	/**
	 * A checkout is recognised by its {@code .git} whether that is the directory a
	 * plain clone leaves or the file a linked worktree does. Insisting on the
	 * directory read a worktree as uncloned and ran {@code git clone} into a
	 * directory that was not empty, which git refuses — so the step aborted on a
	 * checkout it was meant to reuse, and did so without ever confirming it held the
	 * repository under adoption.
	 */
	private boolean alreadyCloned(AdoptionContext context) {
		Path gitDirectory = context.repositoryDirectory().resolve(".git");
		return Files.exists(gitDirectory);
	}
}
