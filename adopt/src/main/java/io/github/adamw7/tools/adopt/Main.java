package io.github.adamw7.tools.adopt;

import java.nio.file.Path;

import io.github.adamw7.tools.adopt.command.CommandRunner;
import io.github.adamw7.tools.adopt.command.ProcessCommandRunner;

/**
 * Command-line entry point: parses the arguments with {@link CliArguments} —
 * the positional GitHub repository URL, optional workspace directory, and
 * optional feature-branch name, plus the flags for pull-request metadata, the
 * starter-assets step, and the JSON report — and runs the default adoption
 * pipeline against a real {@code git}/{@code claude}/{@code gh} toolchain. A
 * supplied workspace directory is created when it does not yet exist; when
 * omitted, a temporary one is created instead. When {@code --report} names a
 * file, the run's {@link AdoptionReport} is written there as JSON — for a failed
 * run as well as a successful one, so the report can say how far the adoption got
 * and why it stopped.
 */
public class Main {

	public static void main(String[] args) {
		CliArguments cli = CliArguments.parse(args);
		AdoptionContext context = new AdoptionContext(cli.repositoryUrl(), workspace(cli), cli.branchName());
		CommandRunner runner = new ProcessCommandRunner();
		GitHubRepoAdopter adopter = GitHubRepoAdopter.withDefaultPipeline(runner, cli.pullRequestOptions(),
				cli.includeAssets());
		runAndReport(cli, context, adopter);
	}

	/**
	 * Runs the adoption and writes the report on both paths, so a run that fails
	 * part-way still leaves the {@code --report} file behind rather than nothing at
	 * all.
	 *
	 * @return the run's report, once the adoption has completed
	 */
	static AdoptionReport runAndReport(CliArguments cli, AdoptionContext context, GitHubRepoAdopter adopter) {
		AdoptionReport report = new AdoptionReport();
		try {
			adopter.adopt(context, report);
		} catch (RuntimeException e) {
			writeReportSuppressing(cli, context, report, e);
			throw e;
		}
		writeReport(cli, context, report);
		return report;
	}

	static Path workspace(CliArguments cli) {
		return cli.workspace().map(Workspaces::createIfMissing).orElseGet(Workspaces::createTemporary);
	}

	/**
	 * Writes the report of a failed run without letting an unwritable report file
	 * replace the adoption failure being reported — that failure is the diagnostic
	 * the operator needs, so a secondary write error is attached to it rather than
	 * thrown over it.
	 */
	private static void writeReportSuppressing(CliArguments cli, AdoptionContext context, AdoptionReport report,
			RuntimeException failure) {
		try {
			writeReport(cli, context, report);
		} catch (RuntimeException e) {
			failure.addSuppressed(e);
		}
	}

	private static void writeReport(CliArguments cli, AdoptionContext context, AdoptionReport report) {
		cli.reportFile().ifPresent(file -> new AdoptionReportWriter().write(file, context, report));
	}
}
