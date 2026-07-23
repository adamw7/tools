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
 * file, the run's {@link AdoptionReport} is written there as JSON.
 */
public class Main {

	public static void main(String[] args) {
		CliArguments cli = CliArguments.parse(args);
		AdoptionContext context = new AdoptionContext(cli.repositoryUrl(), workspace(cli), cli.branchName());
		CommandRunner runner = new ProcessCommandRunner();
		GitHubRepoAdopter adopter = GitHubRepoAdopter.withDefaultPipeline(runner, cli.pullRequestOptions(),
				cli.includeAssets());
		AdoptionReport report = adopter.adopt(context);
		writeReport(cli, context, report);
	}

	static Path workspace(CliArguments cli) {
		return cli.workspace().map(Workspaces::createIfMissing).orElseGet(Workspaces::createTemporary);
	}

	private static void writeReport(CliArguments cli, AdoptionContext context, AdoptionReport report) {
		cli.reportFile().ifPresent(file -> new AdoptionReportWriter().write(file, context, report));
	}
}
