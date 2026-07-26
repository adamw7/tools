package io.github.adamw7.tools.adopt;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;

import io.github.adamw7.tools.adopt.step.PullRequestOptions;

/**
 * Parses the adoption command line. The first three non-flag arguments are the
 * positional repository URL, workspace directory, and feature-branch name the
 * entry point has always accepted, so existing invocations keep working; the
 * flags expose the rest of the pipeline's configuration: further repositories to
 * adopt in the same run (repeatable {@code --repo}, or {@code --repos <file>} for
 * a file naming one per line), the workspace and branch under their own names
 * ({@code --workspace}, {@code --branch}), the pull-request metadata of
 * {@link PullRequestOptions} ({@code --title}, {@code --body}, repeatable
 * {@code --reviewer}/{@code --label}/{@code --assignee}, and {@code --draft}),
 * the optional starter-assets step ({@code --assets}), the
 * {@code claude-code-enforcer} version to wire into an adopted Maven project
 * ({@code --rule-version}), and a JSON report of the run's outcome
 * ({@code --report <file>}). A blank workspace
 * or branch positional falls back to its default, matching the pre-flag
 * behaviour; an unknown flag or a flag missing its value fails with the usage
 * line rather than being silently ignored.
 *
 * <p>The positional slots keep their meaning whatever else is on the command
 * line — the first is always a repository URL, never a workspace — so a run
 * driven entirely by {@code --repo}/{@code --repos} names its workspace and
 * branch with {@code --workspace} and {@code --branch} rather than positionally.
 * Both flags write the same value as their positional, so naming one twice is the
 * last one winning rather than an error.
 */
public final class CliArguments {

	static final String USAGE = "Usage: [<github-repo-url>] [workspace-directory] [branch-name]"
			+ " [--repo <github-repo-url>]... [--repos <file>]"
			+ " [--workspace <directory>] [--branch <name>]"
			+ " [--title <title>] [--body <body>] [--reviewer <user>]... [--label <label>]..."
			+ " [--assignee <user>]... [--draft] [--assets] [--rule-version <version>] [--report <file>]";

	private final List<String> repositoryUrls = new ArrayList<>();
	private Path workspace;
	private String branchName;
	private String title;
	private String body;
	private final List<String> reviewers = new ArrayList<>();
	private final List<String> labels = new ArrayList<>();
	private final List<String> assignees = new ArrayList<>();
	private boolean draft;
	private boolean assets;
	private String ruleVersion;
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
		cli.requireRepositoryUrls();
		return cli;
	}

	/**
	 * @return every repository to adopt in this run — the positional URL first, then
	 *         the ones the flags added — without duplicates, since the same
	 *         repository twice would adopt one checkout a second time
	 */
	public List<String> repositoryUrls() {
		return RepositoryUrls.distinct(repositoryUrls);
	}

	public Optional<Path> workspace() {
		return Optional.ofNullable(workspace);
	}

	public String branchName() {
		return AdoptionContext.branchOrDefault(branchName);
	}

	public PullRequestOptions pullRequestOptions() {
		return PullRequestOptions.builder()
				.reviewers(reviewers).labels(labels).assignees(assignees).draft(draft)
				.titleIfPresent(title)
				.bodyIfPresent(body)
				.build();
	}

	public boolean includeAssets() {
		return assets;
	}

	/**
	 * @return the released {@code claude-code-enforcer} version to pin into an
	 *         adopted Maven project's POM, or empty to resolve the version of the
	 *         {@code tools} build running the adoption
	 */
	public Optional<String> ruleVersion() {
		return Optional.ofNullable(ruleVersion);
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
			case "--repo" -> consumeValue(args, index, this::addRepository);
			case "--repos" -> consumeValue(args, index, value -> repositoryUrls.addAll(readList(value)));
			case "--workspace" -> consumeValue(args, index, value -> workspace = optionalPath(value));
			case "--branch" -> consumeValue(args, index, value -> branchName = optionalText(value));
			case "--title" -> consumeValue(args, index, value -> title = value);
			case "--body" -> consumeValue(args, index, value -> body = value);
			case "--reviewer" -> consumeValue(args, index, reviewers::add);
			case "--label" -> consumeValue(args, index, labels::add);
			case "--assignee" -> consumeValue(args, index, assignees::add);
			case "--rule-version" -> consumeValue(args, index, value -> ruleVersion = optionalText(value));
			case "--report" -> consumeValue(args, index, value -> reportFile = Path.of(value));
			case "--draft" -> {
				draft = true;
				yield index + 1;
			}
			case "--assets" -> {
				assets = true;
				yield index + 1;
			}
			default -> throw new IllegalArgumentException("Unknown option " + flag + ". " + USAGE);
		};
	}

	private int consumeValue(String[] args, int index, Consumer<String> target) {
		if (index + 1 >= args.length) {
			throw new IllegalArgumentException(args[index] + " requires a value. " + USAGE);
		}
		target.accept(args[index + 1]);
		return index + 2;
	}

	/**
	 * A blank workspace or branch positional counts as not supplied — the rule
	 * {@link Text} defines for every optional input — so it falls back to its
	 * default rather than resolving the empty path or being rejected as an invalid
	 * branch. A blank repository positional is dropped the same way, leaving the run
	 * with whatever the {@code --repo} and {@code --repos} flags named.
	 */
	private void consumePositional(String argument) {
		switch (positionals) {
			case 0 -> addRepository(argument);
			case 1 -> workspace = optionalPath(argument);
			case 2 -> branchName = optionalText(argument);
			default -> throw new IllegalArgumentException("Unexpected argument " + argument + ". " + USAGE);
		}
		positionals++;
	}

	private void addRepository(String url) {
		if (Text.isPresent(url)) {
			repositoryUrls.add(url.strip());
		}
	}

	private List<String> readList(String file) {
		return RepositoryUrls.fromFile(Path.of(Text.required(file, "--repos")));
	}

	private Path optionalPath(String value) {
		return Text.isPresent(value) ? Path.of(value.strip()) : null;
	}

	private String optionalText(String value) {
		return Text.isPresent(value) ? value.strip() : null;
	}

	private void requireRepositoryUrls() {
		if (repositoryUrls.isEmpty()) {
			throw new IllegalArgumentException(USAGE);
		}
	}
}
