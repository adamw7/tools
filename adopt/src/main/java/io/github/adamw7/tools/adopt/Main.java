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
				cli.includeAssets(), cli.ruleVersion());
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
			Failures.alsoRun(e, () -> writeReport(cli, context, report));
			throw e;
		}
		writeReport(cli, context, report);
		return report;
	}

	static Path workspace(CliArguments cli) {
		return Workspaces.resolve(cli.workspace());
	}

	private static void writeReport(CliArguments cli, AdoptionContext context, AdoptionReport report) {
		cli.reportFile().ifPresent(file -> new AdoptionReportWriter().write(file, context, report));
	}
}
