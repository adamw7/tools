package io.github.adamw7.tools.adopt;

import java.util.List;
import java.util.stream.Collectors;

import io.github.adamw7.tools.adopt.command.CommandRunner;
import io.github.adamw7.tools.adopt.command.ProcessCommandRunner;

/**
 * Command-line entry point: parses the arguments with {@link CliArguments} —
 * the repositories to adopt (the positional GitHub repository URL, plus any
 * named with {@code --repo} or listed in a {@code --repos} file), the optional
 * workspace directory, and optional feature-branch name, plus the flags for
 * pull-request metadata, the starter-assets step, and the JSON report — and runs
 * the default adoption pipeline against a real {@code git}/{@code claude}/{@code gh}
 * toolchain. A supplied workspace directory is created when it does not yet
 * exist; when omitted, a temporary one is created instead. Every repository of a
 * run shares that workspace and branch name: each clone lands in its own
 * directory under the workspace, named after the repository.
 *
 * <p>When {@code --report} names a file, the run's {@link AdoptionReport} is
 * written there as JSON — for a failed run as well as a successful one, so the
 * report can say how far the adoption got and why it stopped. A run that adopted
 * several repositories writes the batch document {@link AdoptionReportWriter}
 * describes, one entry per repository.
 *
 * <p>A repository whose adoption fails does not stop the ones behind it: the
 * batch runs to the end and the failures are raised together afterwards, so the
 * process still exits non-zero while the repositories that could be adopted have
 * been.
 */
public class Main {

	public static void main(String[] args) {
		CliArguments cli = CliArguments.parse(args);
		CommandRunner runner = new ProcessCommandRunner();
		GitHubRepoAdopter adopter = GitHubRepoAdopter.withDefaultPipeline(runner, cli.pullRequestOptions(),
				cli.includeAssets(), cli.ruleVersion());
		runAndReport(cli, contexts(cli), adopter);
	}

	/**
	 * Runs the adoption of every requested repository and writes the report on both
	 * paths, so a run that fails part-way still leaves the {@code --report} file
	 * behind rather than nothing at all.
	 *
	 * @return the runs, once every repository has been attempted
	 * @throws AdoptionException when any repository's adoption failed, naming each
	 *                           one and why it stopped
	 */
	static List<AdoptionRun> runAndReport(CliArguments cli, List<AdoptionContext> contexts,
			GitHubRepoAdopter adopter) {
		List<AdoptionRun> runs = new BatchAdoption(adopter::adopt).adoptAll(contexts);
		try {
			requireEveryAdoptionSucceeded(runs);
		} catch (RuntimeException e) {
			Failures.alsoRun(e, () -> writeReport(cli, runs));
			throw e;
		}
		writeReport(cli, runs);
		return runs;
	}

	/** Every repository of a run is adopted into one workspace, on one branch name. */
	static List<AdoptionContext> contexts(CliArguments cli) {
		return Checkouts.forRun(cli.repositoryUrls(), Workspaces.resolve(cli.workspace()), cli.branchName());
	}

	/**
	 * Fails the run when any repository's adoption did, naming every failure rather
	 * than only the first: an operator who started a batch needs to know which
	 * repositories to re-run, and the count says at a glance how much of the batch
	 * landed.
	 */
	private static void requireEveryAdoptionSucceeded(List<AdoptionRun> runs) {
		List<AdoptionRun> failed = runs.stream().filter(run -> !run.succeeded()).toList();
		if (!failed.isEmpty()) {
			throw new AdoptionException("Adoption failed for " + failed.size() + " of " + runs.size()
					+ " repositories: " + describe(failed));
		}
	}

	private static String describe(List<AdoptionRun> failed) {
		return failed.stream()
				.map(run -> run.repositoryUrl() + ": " + run.failure().orElse("unknown failure"))
				.collect(Collectors.joining("; "));
	}

	private static void writeReport(CliArguments cli, List<AdoptionRun> runs) {
		cli.reportFile().ifPresent(file -> new AdoptionReportWriter().write(file, runs));
	}
}
