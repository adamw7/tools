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
import io.github.adamw7.tools.secret.Redaction;
import io.github.adamw7.tools.adopt.command.CommandResult;
import io.github.adamw7.tools.adopt.command.CommandRunner;

/**
 * Clones the target repository into the workspace with {@code git clone}, so the
 * remaining steps have a working checkout to operate on. The step is idempotent:
 * a checkout that already exists (its {@code .git} directory is present) is
 * reused rather than re-cloned, so re-running the adoption against the same
 * workspace does not abort on an "already exists" clone failure.
 *
 * <p>A reused checkout is confirmed to be the repository under adoption first, by
 * comparing what its {@code origin} names with the URL the run was given. A
 * checkout directory is named after the repository alone, so {@code alice/tools}
 * and {@code bob/tools} claim one directory under a named {@code --workspace};
 * {@link io.github.adamw7.tools.adopt.Checkouts} catches that within a single run,
 * but two runs against the same workspace never meet, and the second adoption would
 * otherwise branch, commit and push the first repository's working tree.
 *
 * <p>A reused checkout must also be free of uncommitted work that is not the
 * adoption's own, since {@link CommitStep} stages the whole tree and would push a
 * contributor's work in progress as part of the adoption.
 *
 * <p>The checkout is then refreshed with {@code git fetch}, because every later
 * decision about the feature branch is taken against the remote-tracking refs a
 * stale checkout has out of date. That fetch carries the credentials the run was
 * given rather than the credential-free {@code origin} left behind for it — see
 * {@link #fetchCommand}.
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
	 * wholly-untracked directory into one entry. Without it the {@code .claude/} the
	 * adoption had just created was reported as that one path, which is not among
	 * {@link AdoptionAssets#WRITTEN_PATHS}, so a resumed adoption was refused for
	 * changes that were its own — the common case, since a repository being adopted
	 * has no {@code .claude/} to begin with.
	 */
	private static final String UNTRACKED_FILES_ALL = "--untracked-files=all";

	/** What {@code git status --porcelain} puts between a rename's old and new path. */
	private static final String RENAME_ARROW = " -> ";

	/**
	 * Keeps a fetch from recording the URL it fetched through in
	 * {@code .git/FETCH_HEAD}, where a credentialled one would outlive the run — the
	 * leak {@link #forgetCredentials} closes in {@code .git/config}. Nothing here
	 * reads that file. Only the credentialled fetch asks for the option, so a git too
	 * old to know it is unaffected unless the run carries credentials.
	 */
	private static final String NO_FETCH_HEAD = "--no-write-fetch-head";

	/**
	 * One entry of {@code git status --porcelain}: the two status letters git
	 * documents, then the space before the path. The transcript merges standard error
	 * in, so it is not reliably entries and nothing else — reading every line as one
	 * took a warning's fourth character onwards for a path and refused the resume this
	 * step promises. At least one letter has to say something, since git never reports
	 * a path unchanged in both index and work tree, and accepting two spaces let
	 * through any line indented by three.
	 */
	private static final Pattern STATUS_ENTRY = Pattern.compile("^(?! {2})[ MTADRCU?!]{2} .+");

	@Override
	public String name() {
		return "clone";
	}

	/**
	 * Records the checkout before doing anything to it, so a run that fails part-way
	 * still says where the working tree it left behind is — a caller that named no
	 * workspace was given a temporary one, and a dry run leaves that directory as the
	 * only thing to read.
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
	 * same URL: {@code git clone} writes the URL it was handed into
	 * {@code .git/config} verbatim, so an adoption driven by CI left its
	 * {@code x-access-token:TOKEN} in plaintext for as long as the workspace survives.
	 * Masking keeps it out of what the run <em>says</em>; only this keeps it out of
	 * what the run <em>leaves behind</em>.
	 *
	 * <p>The two commands that still authenticate are unaffected, neither reading the
	 * URL back from the checkout: {@link PushStep} supplies it as a push-URL override
	 * and {@link #fetchCommand} as a remote rewrite, each for one invocation and
	 * written nowhere. Only the remote this step just wrote is rewritten, and only
	 * when the URL carried credentials — a reused checkout's {@code origin} is its
	 * owner's, not the adoption's to edit.
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
	 * Refuses a reused checkout carrying uncommitted work that is not the adoption's.
	 * {@link CommitStep} stages the whole tree with {@code git add -A}, so a
	 * contributor's work in progress would be swept into the adoption's commit and
	 * offered for review as part of adopting Claude Code. Only unrelated paths stop
	 * the run: an adoption that failed between writing a file and committing it leaves
	 * exactly the paths {@link AdoptionAssets} names, so it still resumes — the case
	 * worth resuming, having already paid for a {@code claude init}.
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
	 * The paths one entry changes. A rename or copy is reported as {@code old -> new}
	 * and changes <em>both</em> sides: reading only the destination waved the entry
	 * through whenever that destination was a path the adoption writes, sweeping a
	 * staged {@code git mv docs/CLAUDE.md CLAUDE.md} into the adoption's commit.
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
	 * The transcript merges standard error in, so the {@code origin} URL is not
	 * reliably the whole of it and reading it as one URL failed a checkout that was
	 * right all along. Every line is asked in turn, keeping the conservative reading
	 * {@link AdoptionContext#isSameRepository} is built on: noise names no repository.
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
	 * get-url}, which expands the caller's {@code url.<base>.insteadOf} rewrites and so
	 * answers a URL the checkout never recorded — a rewrite onto a mirror names a
	 * different host and path, refusing the reused checkout in exactly the
	 * environments that configure one.
	 */
	private String originTranscript(AdoptionContext context, CommandRunner runner) {
		return askGit(context, runner,
				List.of("git", "config", "--get-all", "remote." + AdoptionContext.REMOTE + ".url"),
				"is a checkout with no '" + AdoptionContext.REMOTE + "' remote, so it can be neither confirmed to be "
						+ context.displayUrl() + " nor pushed to it");
	}

	/**
	 * Puts a question to git about the checkout being reused, answering with the
	 * transcript. A query that could not be <em>run</em> stops the run, saying which
	 * directory, what could not be established about it, and only then git's own words.
	 *
	 * <p>Both questions come through here because both read their answer out of the
	 * transcript, and one empty because the command never ran is indistinguishable
	 * from one empty because the answer was "nothing" — a checkout with no
	 * {@code origin} read as one whose {@code origin} matched.
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
	 * resumes from its tip. A checkout predating that push carries no
	 * {@code origin/<branch>}, restarting the branch at the default branch and leaving
	 * {@link PushStep} refused as a non-fast-forward.
	 */
	private void refresh(AdoptionContext context, CommandRunner runner) {
		log.info("Fetching {} in {}", AdoptionContext.REMOTE, context.repositoryDirectory());
		runOrFail(runner, context.repositoryDirectory(), fetchCommand(context));
	}

	/**
	 * Fetches through the credentials the run was given, the {@code origin} of a
	 * reused checkout having none — {@link #forgetCredentials} took them out the
	 * moment the clone put them there. A plain {@code git fetch origin} reaches a
	 * private repository anonymously and is refused, so an adoption driven by CI into
	 * a kept workspace failed on the very step that exists to resume it.
	 *
	 * <p>The credentials go to this one invocation, written nowhere. They cannot go
	 * through {@code -c remote.origin.url}, which git reads as <em>another</em> of
	 * that key's values and resolves the configured one first, nor through a
	 * positional URL, which git records verbatim in the reflog of every ref it
	 * updates. A transient {@code insteadOf} rewrite leaves the fetch naming the
	 * remote, so the reflog records only {@code fetch origin}, and
	 * {@link #NO_FETCH_HEAD} keeps the rewritten URL out of {@code .git/FETCH_HEAD}.
	 *
	 * <p>The rewrite is keyed on {@link AdoptionContext#checkoutUrl()}, the
	 * {@code origin} of every checkout this step cloned. A checkout somebody else set
	 * up may name the repository another way, which the rewrite does not match and so
	 * leaves the fetch exactly as it was — their {@code origin}, their credentials.
	 */
	private List<String> fetchCommand(AdoptionContext context) {
		if (context.checkoutUrl().equals(context.repositoryUrl())) {
			return List.of("git", "fetch", AdoptionContext.REMOTE);
		}
		return List.of("git", "-c", credentialledRewrite(context), "fetch", NO_FETCH_HEAD, AdoptionContext.REMOTE);
	}

	/** Rewrites the credential-free {@code origin} back to the URL the run was given. */
	private String credentialledRewrite(AdoptionContext context) {
		return "url." + context.repositoryUrl() + ".insteadOf=" + context.checkoutUrl();
	}

	/**
	 * A checkout is recognised by its {@code .git}, whether the directory a plain
	 * clone leaves or the file a linked worktree does. Insisting on the directory read
	 * a worktree as uncloned and ran {@code git clone} into a non-empty directory,
	 * aborting on a checkout it was meant to reuse.
	 */
	private boolean alreadyCloned(AdoptionContext context) {
		Path gitDirectory = context.repositoryDirectory().resolve(".git");
		return Files.exists(gitDirectory);
	}
}
