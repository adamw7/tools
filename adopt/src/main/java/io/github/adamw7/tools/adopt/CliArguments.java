package io.github.adamw7.tools.adopt;

import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;

import io.github.adamw7.tools.adopt.step.PullRequestOptions;

/**
 * Parses the adoption command line. The first three non-flag arguments are the
 * positional repository URL, workspace directory, and feature-branch name the
 * entry point has always accepted; the flags expose the rest of the pipeline's
 * configuration: further repositories for the same run (repeatable
 * {@code --repo}, or {@code --repos <file>} for a file naming one per line), the
 * workspace and branch under their own names, the {@link PullRequestOptions}
 * metadata ({@code --title}, {@code --body}, repeatable
 * {@code --reviewer}/{@code --label}/{@code --assignee}, {@code --draft}), the
 * starter-assets step ({@code --assets}), the {@code claude-code-enforcer}
 * version to wire in ({@code --rule-version}), a rehearsal that publishes nothing
 * ({@code --dry-run}), how long any one external command may take
 * ({@code --timeout <minutes>}), and a JSON report of the outcome
 * ({@code --report <file>}). A blank workspace or branch positional falls back to
 * its default; an unknown flag, or one missing its value, fails with the usage
 * line rather than being ignored — as does {@code --help}, which asks for that
 * line and so is answered with it instead.
 *
 * <p>The positional slots keep their meaning whatever else is on the command line
 * — the first is always a repository URL, never a workspace — so a run driven
 * entirely by {@code --repo}/{@code --repos} names its workspace and branch with
 * {@code --workspace} and {@code --branch}. A flag and its positional write the
 * same value, so naming one twice is the last one winning rather than an error.
 */
public final class CliArguments {

	static final String USAGE = "Usage: [<github-repo-url>] [workspace-directory] [branch-name]"
			+ " [--repo <github-repo-url>]... [--repos <file>]"
			+ " [--workspace <directory>] [--branch <name>]"
			+ " [--title <title>] [--body <body>] [--reviewer <user>]... [--label <label>]..."
			+ " [--assignee <user>]... [--draft] [--assets] [--rule-version <version>]"
			+ " [--dry-run] [--timeout <minutes>] [--report <file>] [--help]";

	static final String HELP_FLAG = "--help";
	static final String HELP_SHORTHAND = "-h";

	private static final String TIMEOUT_FLAG = "--timeout";

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
	private boolean dryRun;
	private Duration commandTimeout;
	private Path reportFile;
	private boolean help;
	private int positionals;
	private String positionalUrl;
	private boolean flagsNamedARepository;

	private CliArguments() {
	}

	public static CliArguments parse(String[] args) {
		CliArguments cli = new CliArguments();
		String[] arguments = args == null ? new String[0] : args;
		int index = 0;
		while (index < arguments.length) {
			index = cli.consume(arguments, index);
		}
		cli.requireSomethingToAdopt();
		return cli;
	}

	/**
	 * @return whether the run asked for the usage line rather than for an adoption,
	 *         with {@code --help} or {@code -h}
	 */
	public boolean helpRequested() {
		return help;
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
		return new PullRequestOptions(title, body, reviewers, labels, assignees, draft);
	}

	/** How this run is configured, as the pipeline and the command runner read it. */
	public AdoptionOptions adoptionOptions() {
		return new AdoptionOptions(pullRequestOptions(), assets, ruleVersion, dryRun, commandTimeout);
	}

	public Optional<Path> reportFile() {
		return Optional.ofNullable(reportFile);
	}

	/**
	 * {@code -h} is tested before the {@code --} prefix, since it carries none and
	 * would otherwise be read as the run's first positional — a repository URL,
	 * which is the one reading of it nobody means.
	 */
	private int consume(String[] args, int index) {
		String argument = args[index];
		if (HELP_FLAG.equals(argument) || HELP_SHORTHAND.equals(argument)) {
			help = true;
			return index + 1;
		}
		if (argument.startsWith("--")) {
			return consumeFlag(args, index);
		}
		consumePositional(argument);
		return index + 1;
	}

	private int consumeFlag(String[] args, int index) {
		String flag = args[index];
		return switch (flag) {
			case "--repo" -> consumeName(args, index, this::addFlaggedRepository);
			case "--repos" -> consumeName(args, index, value -> addFlaggedRepositories(readList(value)));
			case "--workspace" -> consumeName(args, index, value -> workspace = optionalPath(value));
			case "--branch" -> consumeName(args, index, value -> branchName = optionalText(value));
			case "--title" -> consumeValue(args, index, value -> title = value);
			case "--body" -> consumeValue(args, index, value -> body = value);
			case "--reviewer" -> consumeName(args, index, reviewers::add);
			case "--label" -> consumeName(args, index, labels::add);
			case "--assignee" -> consumeName(args, index, assignees::add);
			case "--rule-version" -> consumeName(args, index, value -> ruleVersion = optionalText(value));
			case TIMEOUT_FLAG -> consumeName(args, index, value -> commandTimeout = optionalTimeout(value));
			case "--report" -> consumeName(args, index, value -> reportFile = optionalPath(value));
			case "--draft" -> {
				draft = true;
				yield index + 1;
			}
			case "--assets" -> {
				assets = true;
				yield index + 1;
			}
			case "--dry-run" -> {
				dryRun = true;
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
	 * Reads the value of a flag that names something — a URL, a path, a branch, a
	 * user, a number — none of which is ever spelled as a flag. A missing value would
	 * otherwise swallow the next flag as the value: {@code --branch --draft} named a
	 * branch called {@code --draft}, created it, and pushed it, with the draft the
	 * operator asked for silently dropped. {@code --title} and {@code --body} take
	 * free-form prose and so keep {@link #consumeValue}, which accepts whatever
	 * follows.
	 */
	private int consumeName(String[] args, int index, Consumer<String> target) {
		if (index + 1 < args.length && args[index + 1].startsWith("--")) {
			throw new IllegalArgumentException(args[index] + " requires a value, but was followed by the option "
					+ args[index + 1] + ". " + USAGE);
		}
		return consumeValue(args, index, target);
	}

	/**
	 * A blank workspace or branch positional counts as not supplied — the rule
	 * {@link Text} defines for every optional input, and the one every optional flag
	 * value follows too — so it falls back to its default rather than resolving the
	 * empty path or being rejected as an invalid branch. A blank repository
	 * positional is dropped the same way, leaving the run with whatever the
	 * {@code --repo} and {@code --repos} flags named.
	 */
	private void consumePositional(String argument) {
		switch (positionals) {
			case 0 -> addPositionalRepository(argument);
			case 1 -> workspace = optionalPath(argument);
			case 2 -> branchName = optionalText(argument);
			default -> throw new IllegalArgumentException("Unexpected argument " + argument + ". " + USAGE);
		}
		positionals++;
	}

	private void addPositionalRepository(String url) {
		positionalUrl = optionalText(url);
		addRepository(url);
	}

	private void addFlaggedRepositories(List<String> urls) {
		urls.forEach(this::addFlaggedRepository);
	}

	private void addFlaggedRepository(String url) {
		flagsNamedARepository = flagsNamedARepository || Text.isPresent(url);
		addRepository(url);
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

	/**
	 * The timeout is read as whole minutes rather than as an ISO-8601 duration,
	 * because it is set in the units the operator is reasoning about — how long a
	 * {@code claude init} over their largest repository takes. A blank value counts
	 * as not supplied, like every other optional flag's, and falls back to the
	 * pipeline's default.
	 */
	private Duration optionalTimeout(String value) {
		return Text.isPresent(value) ? Duration.ofMinutes(minutes(value.strip())) : null;
	}

	private long minutes(String value) {
		long parsed = parseMinutes(value);
		if (parsed <= 0) {
			throw new IllegalArgumentException(
					TIMEOUT_FLAG + " must be a positive number of minutes, but was " + value + ". " + USAGE);
		}
		return parsed;
	}

	private long parseMinutes(String value) {
		try {
			return Long.parseLong(value);
		} catch (NumberFormatException e) {
			throw new IllegalArgumentException(
					TIMEOUT_FLAG + " must be a whole number of minutes, but was " + value + ". " + USAGE, e);
		}
	}

	/**
	 * A run that asked for the usage line is not asked to adopt anything, so the
	 * repository requirements are not applied to it: {@code --help} on its own would
	 * otherwise be refused with the very line it asked to be shown.
	 */
	private void requireSomethingToAdopt() {
		if (!help) {
			requireRepositoryUrls();
			requirePositionalNamesARepository();
		}
	}

	private void requireRepositoryUrls() {
		if (repositoryUrls.isEmpty()) {
			throw new IllegalArgumentException(USAGE);
		}
	}

	/**
	 * Rejects a first positional that names no repository owner when the flags already
	 * named repositories — {@code --repos list.txt /tmp/workspace}, where the operator
	 * meant the workspace the flags left unnamed. The positional slot keeps its
	 * meaning whatever else is on the command line, so that path is read as a
	 * repository URL and fails several steps later on a clone of a directory that is
	 * not a repository. Saying so here names the argument and the flag meant instead.
	 *
	 * <p>The check is only worth making when the flags supplied a repository, since a
	 * run whose only repository is the positional has nothing else it could be. A
	 * local path really is adoptable — only a URL with a host names an owner — so a
	 * batch of local repositories names them all with {@code --repo}.
	 *
	 * <p>The argument is named through {@link Redaction}, as every other message the
	 * adoption raises about a URL is. A URL naming no owner can still carry
	 * credentials — {@code https://x-access-token:TOKEN@github.com/repo}, whose
	 * userinfo and host collapse into one segment where the owner should be — and this
	 * message is the last thing a run prints before it stops, so reporting the
	 * argument verbatim wrote the token to the operator's terminal and to whatever
	 * captured it.
	 */
	private void requirePositionalNamesARepository() {
		if (positionalUrl != null && flagsNamedARepository && namesNoOwner(positionalUrl)) {
			throw new IllegalArgumentException("The first argument is read as a repository URL, but "
					+ Redaction.of(positionalUrl) + " names no repository owner. Name the workspace with --workspace,"
					+ " or the repository with --repo. " + USAGE);
		}
	}

	private boolean namesNoOwner(String url) {
		return RepositoryUrl.of(url).slug().isEmpty();
	}
}
