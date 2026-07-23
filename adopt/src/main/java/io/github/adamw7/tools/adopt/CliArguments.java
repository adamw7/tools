package io.github.adamw7.tools.adopt;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import io.github.adamw7.tools.adopt.step.PullRequestOptions;

/**
 * Parses the adoption command line. The first three non-flag arguments are the
 * positional repository URL, workspace directory, and feature-branch name the
 * entry point has always accepted, so existing invocations keep working; the
 * flags expose the rest of the pipeline's configuration: the pull-request
 * metadata of {@link PullRequestOptions} ({@code --title}, {@code --body},
 * repeatable {@code --reviewer}/{@code --label}/{@code --assignee}, and
 * {@code --draft}), the optional starter-assets step ({@code --assets}), and a
 * JSON report of the run's outcome ({@code --report <file>}). A blank workspace
 * or branch positional falls back to its default, matching the pre-flag
 * behaviour; an unknown flag or a flag missing its value fails with the usage
 * line rather than being silently ignored.
 */
public final class CliArguments {

	static final String USAGE = "Usage: <github-repo-url> [workspace-directory] [branch-name]"
			+ " [--title <title>] [--body <body>] [--reviewer <user>]... [--label <label>]..."
			+ " [--assignee <user>]... [--draft] [--assets] [--report <file>]";

	private String repositoryUrl;
	private Path workspace;
	private String branchName;
	private String title;
	private String body;
	private final List<String> reviewers = new ArrayList<>();
	private final List<String> labels = new ArrayList<>();
	private final List<String> assignees = new ArrayList<>();
	private boolean draft;
	private boolean assets;
	private Path reportFile;
	private int positionals;

	private CliArguments() {
	}

	public static CliArguments parse(String[] args) {
		CliArguments cli = new CliArguments();
		int index = 0;
		while (args != null && index < args.length) {
			index = cli.consume(args, index);
		}
		cli.requireRepositoryUrl();
		return cli;
	}

	public String repositoryUrl() {
		return repositoryUrl;
	}

	public Optional<Path> workspace() {
		return Optional.ofNullable(workspace);
	}

	public String branchName() {
		return branchName == null ? AdoptionContext.DEFAULT_BRANCH : branchName;
	}

	public PullRequestOptions pullRequestOptions() {
		PullRequestOptions.Builder builder = PullRequestOptions.builder()
				.reviewers(reviewers).labels(labels).assignees(assignees).draft(draft);
		if (title != null) {
			builder.title(title);
		}
		if (body != null) {
			builder.body(body);
		}
		return builder.build();
	}

	public boolean includeAssets() {
		return assets;
	}

	public Optional<Path> reportFile() {
		return Optional.ofNullable(reportFile);
	}

	private int consume(String[] args, int index) {
		String argument = args[index];
		if (argument.startsWith("--")) {
			return consumeFlag(args, index);
		}
		consumePositional(argument);
		return index + 1;
	}

	private int consumeFlag(String[] args, int index) {
		String flag = args[index];
		return switch (flag) {
			case "--title" -> consumeValue(args, index, value -> title = value);
			case "--body" -> consumeValue(args, index, value -> body = value);
			case "--reviewer" -> consumeValue(args, index, reviewers::add);
			case "--label" -> consumeValue(args, index, labels::add);
			case "--assignee" -> consumeValue(args, index, assignees::add);
			case "--report" -> consumeValue(args, index, value -> reportFile = Path.of(value));
			case "--draft" -> consumeSwitch(index, () -> draft = true);
			case "--assets" -> consumeSwitch(index, () -> assets = true);
			default -> throw new IllegalArgumentException("Unknown option " + flag + ". " + USAGE);
		};
	}

	private int consumeValue(String[] args, int index, java.util.function.Consumer<String> target) {
		if (index + 1 >= args.length) {
			throw new IllegalArgumentException(args[index] + " requires a value. " + USAGE);
		}
		target.accept(args[index + 1]);
		return index + 2;
	}

	private int consumeSwitch(int index, Runnable target) {
		target.run();
		return index + 1;
	}

	private void consumePositional(String argument) {
		assignPositional(positionals, argument);
		positionals++;
	}

	private void assignPositional(int position, String argument) {
		switch (position) {
			case 0 -> repositoryUrl = argument;
			case 1 -> workspace = argument.isBlank() ? null : Path.of(argument);
			case 2 -> branchName = argument.isBlank() ? null : argument;
			default -> throw new IllegalArgumentException("Unexpected argument " + argument + ". " + USAGE);
		}
	}

	private void requireRepositoryUrl() {
		if (repositoryUrl == null || repositoryUrl.isBlank()) {
			throw new IllegalArgumentException(USAGE);
		}
	}
}
