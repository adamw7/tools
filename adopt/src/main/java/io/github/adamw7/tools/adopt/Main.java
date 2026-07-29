package io.github.adamw7.tools.adopt;

import java.util.List;
import java.util.stream.Collectors;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import io.github.adamw7.tools.adopt.command.CommandRunner;
import io.github.adamw7.tools.adopt.command.ProcessCommandRunner;

/**
 * Command-line entry point: parses the arguments with {@link CliArguments} and
 * runs the default adoption pipeline against a real
 * {@code git}/{@code claude}/{@code gh} toolchain. Every repository of a run
 * shares one workspace — the one supplied, created when it does not exist, or a
 * temporary one — and one branch name, each clone landing in its own directory
 * under the workspace, named after the repository.
 *
 * <p>When {@code --report} names a file, the run's {@link AdoptionReport} is
 * written there as JSON, for a failed run as well as a successful one, so it can
 * say how far the adoption got and why it stopped. A run over several
 * repositories writes the batch document {@link AdoptionReportWriter} describes.
 *
 * <p>{@code --help} is answered with the usage line and nothing is adopted, so the
 * flag every operator reaches for first is not refused as an unknown option. The
 * line goes to the log, which the console appender writes to standard error: the
 * same jar is the adoption MCP server, whose stdio transport owns standard output.
 *
 * <p>A repository whose adoption fails does not stop the ones behind it — nor does
 * one whose URL names no repository at all: the batch runs to the end and the
 * failures are raised together afterwards, so the process still exits non-zero.
 */
public class Main {

	private static final Logger log = LogManager.getLogger(Main.class);

	public static void main(String[] args) {
		CliArguments cli = CliArguments.parse(args);
		if (cli.helpRequested()) {
			log.info(CliArguments.USAGE);
			return;
		}
		AdoptionOptions options = cli.adoptionOptions();
		CommandRunner runner = new ProcessCommandRunner(options.commandTimeout());
		runAndReport(cli, checkouts(cli), GitHubRepoAdopter.withDefaultPipeline(runner, options));
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
	static List<AdoptionRun> runAndReport(CliArguments cli, Checkouts checkouts, GitHubRepoAdopter adopter) {
		List<AdoptionRun> runs = new BatchAdoption(adopter::adopt).adoptAll(cli.repositoryUrls(), checkouts);
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
	static Checkouts checkouts(CliArguments cli) {
		return new Checkouts(Workspaces.resolve(cli.workspace()), cli.branchName());
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
