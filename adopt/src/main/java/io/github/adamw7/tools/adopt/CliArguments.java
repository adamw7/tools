package io.github.adamw7.tools.adopt;

import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import io.github.adamw7.tools.adopt.step.GuardRules;
import io.github.adamw7.tools.adopt.step.PullRequestOptions;
import io.github.adamw7.tools.secret.Redaction;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;
import picocli.CommandLine.ParameterException;

/**
 * Parses the adoption command line. The first three non-flag arguments are the
 * positional repository URL, workspace directory, and feature-branch name; the
 * flags expose the rest of the pipeline's configuration — further repositories for
 * the same run (repeatable {@code --repo}, or {@code --repos <file>} for a file
 * naming one per line), the workspace and branch under their own names, the
 * {@link PullRequestOptions} metadata, the starter-assets step, the
 * {@code claude-code-enforcer} version to wire in, a rehearsal that publishes
 * nothing, how long any one external command may take, how many further attempts a
 * command the network refused earns, and a JSON report of the outcome. A blank
 * value counts as not supplied for every optional input; an unknown flag, or one
 * missing its value, fails with the usage line.
 *
 * <p>The arguments are matched by picocli rather than by a loop of this module's
 * own, so what the parser accepts is declared once. {@code --repo} and
 * {@code --repos} are bound to methods because picocli calls a method option once
 * per occurrence, in the order the operator wrote it — the one thing a batch cannot
 * lose. The workspace and branch are bound to methods too, because a flag and its
 * positional write the one field and the last one named has to win, which is what
 * {@code setOverwrittenOptionsAllowed} buys.
 *
 * <p>A flag that names something is never followed by another flag, and picocli
 * refuses one that is: {@code --branch --draft} named a branch called
 * {@code --draft}, created it, and pushed it, with the draft silently dropped.
 * {@code --title} and {@code --body} take free-form prose, and prose that looks
 * like a flag is still prose — the refusal is for a value that is an option of this
 * command, not for any word opening with dashes.
 *
 * <p>The usage line is written out rather than generated, picocli ordering a
 * generated synopsis by reflection over members the JVM reports in no promised
 * order. This one is ordered to be read: positionals, then what to adopt, then the
 * pull request's metadata.
 */
@Command(name = "adopt")
public final class CliArguments {

	static final String USAGE = "Usage: [<github-repo-url>] [workspace-directory] [branch-name]"
			+ " [--repo <github-repo-url>]... [--repos <file>]"
			+ " [--workspace <directory>] [--branch <name>]"
			+ " [--title <title>] [--body <body>] [--reviewer <user>]... [--label <label>]..."
			+ " [--assignee <user>]... [--draft] [--assets] [--rule-version <version>]"
			+ " [--rules <minimal|project>] [--section <heading>]..."
			+ " [--dry-run] [--verify-only] [--keep-workspace] [--parallel <count>]"
			+ " [--timeout <minutes>] [--retries <count>]"
			+ " [--report <file>] [--help]";

	static final String HELP_FLAG = "--help";
	static final String HELP_SHORTHAND = "-h";

	private static final String PARALLEL_FLAG = "--parallel";
	private static final String TIMEOUT_FLAG = "--timeout";
	private static final String RETRIES_FLAG = "--retries";
	private static final String REPOS_FLAG = "--repos";

	/** {@link AdoptionOptions#MAX_TIMEOUT} in the units this flag is written in. */
	private static final long MAX_TIMEOUT_MINUTES = AdoptionOptions.MAX_TIMEOUT.toMinutes();

	@Option(names = "--title", paramLabel = "<title>")
	private String title;

	@Option(names = "--body", paramLabel = "<body>")
	private String body;

	@Option(names = "--reviewer", paramLabel = "<user>")
	private List<String> reviewers = new ArrayList<>();

	@Option(names = "--label", paramLabel = "<label>")
	private List<String> labels = new ArrayList<>();

	@Option(names = "--assignee", paramLabel = "<user>")
	private List<String> assignees = new ArrayList<>();

	/**
	 * How much of the adopted repository's configuration the guard checks. Refused
	 * when it names neither rule set, since a misspelt value read as the default would
	 * quietly change what somebody else's build enforces.
	 */
	@Option(names = "--rules", paramLabel = "<minimal|project>")
	private String rules;

	/**
	 * A {@code CLAUDE.md} heading the guard is to demand, repeatable. Naming any
	 * replaces the set the detected build system would have asked for.
	 */
	@Option(names = "--section", paramLabel = "<heading>")
	private List<String> sections = new ArrayList<>();

	@Option(names = "--rule-version", paramLabel = "<version>")
	private String ruleVersion;

	@Option(names = "--report", paramLabel = "<file>")
	private String reportFile;

	@Option(names = "--draft")
	private boolean draft;

	@Option(names = "--assets")
	private boolean assets;

	/**
	 * Keeps every checkout, whatever the outcome. Without it one whose adoption landed
	 * is removed — its product is a pushed branch and a pull request, and a batch of
	 * fifty full clones left fifty behind. A failed adoption's checkout is kept either
	 * way, as is a dry run's, that being all a dry run produces.
	 */
	@Option(names = "--keep-workspace")
	private boolean keepWorkspace;

	/**
	 * Reports whether each repository is still adopted and its guard still passes,
	 * without adopting anything: a CLAUDE.md a later commit deleted, or a guard
	 * someone removed, leaves a repository looking adopted and checking nothing.
	 */
	@Option(names = "--verify-only")
	private boolean verifyOnly;

	/** How many repositories are adopted at once; see {@link BatchAdoption}. */
	@Option(names = PARALLEL_FLAG, paramLabel = "<count>")
	private String parallel;

	@Option(names = "--dry-run")
	private boolean dryRun;

	@Option(names = { HELP_FLAG, HELP_SHORTHAND })
	private boolean help;

	private final List<String> repositoryUrls = new ArrayList<>();
	private Path workspace;
	private String branchName;
	private Duration commandTimeout;
	private int retries = AdoptionOptions.DEFAULT_RETRIES;
	private String positionalUrl;
	private boolean flagsNamedARepository;

	private CliArguments() {
	}

	public static CliArguments parse(String[] args) {
		CliArguments cli = new CliArguments();
		String[] arguments = args == null ? new String[0] : args;
		CommandLine parser = new CommandLine(cli).setOverwrittenOptionsAllowed(true);
		try {
			parser.parseArgs(arguments);
		} catch (ParameterException e) {
			return helpOrRefusal(cli, parser, arguments, e);
		}
		cli.requireSomethingToAdopt();
		return cli;
	}

	/**
	 * A command line asking for the usage line gets it even when another argument
	 * could not be read: picocli calls an option's method as it parses, so an
	 * unreadable {@code --repos} file is raised while {@code --help} is still only a
	 * flag further along the list.
	 */
	private static CliArguments helpOrRefusal(CliArguments cli, CommandLine parser, String[] args,
			ParameterException e) {
		if (asksForHelp(parser, args)) {
			cli.help = true;
			return cli;
		}
		throw refusal(e);
	}

	/**
	 * Whether the command line asked for the usage line, rather than merely carrying
	 * the word. A help flag written where a value was expected is that value —
	 * {@code --title -h} names a title, badly. Reading every occurrence as a request
	 * answered {@code --repo <url> --title -h} with the usage line and exited zero,
	 * having adopted nothing.
	 */
	private static boolean asksForHelp(CommandLine parser, String[] args) {
		Set<String> valueOptions = optionsTakingAValue(parser);
		return IntStream.range(0, args.length)
				.filter(index -> isHelpFlag(args[index]))
				.anyMatch(index -> index == 0 || !valueOptions.contains(args[index - 1]));
	}

	private static boolean isHelpFlag(String argument) {
		return HELP_FLAG.equals(argument) || HELP_SHORTHAND.equals(argument);
	}

	/**
	 * The options that consume the argument after them, read from the declarations
	 * above so an option added to this command cannot swallow a help flag.
	 */
	private static Set<String> optionsTakingAValue(CommandLine parser) {
		return parser.getCommandSpec().options().stream()
				.filter(option -> option.arity().max() > 0)
				.flatMap(option -> Stream.of(option.names()))
				.collect(Collectors.toSet());
	}

	/**
	 * @return whether the run asked for the usage line rather than for an adoption,
	 *         with {@code --help} or {@code -h}
	 */
	public boolean helpRequested() {
		return help;
	}

	/**
	 * @return every repository to adopt, in the order named and without duplicates —
	 *         the same repository twice would adopt one checkout a second time
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
		return new AdoptionOptions(pullRequestOptions(), assets, ruleVersion, dryRun, commandTimeout, retries,
				guardRules(), sections, verifyOnly);
	}

	/**
	 * @return the rule set named, or the default when none was. A blank value counts
	 *         as none; anything else that is not a rule set is refused by
	 *         {@link GuardRules#of}.
	 */
	private GuardRules guardRules() {
		return rules == null || rules.isBlank() ? GuardRules.PROJECT : GuardRules.of(rules);
	}

	/**
	 * @return how many repositories to adopt at once, bounded here as well as in
	 *         {@link BatchAdoption} so a bad value fails before the first clone
	 */
	public int parallelism() {
		if (parallel == null || parallel.isBlank()) {
			return BatchAdoption.SEQUENTIAL;
		}
		return requireWithinBounds(parsed(parallel));
	}

	private int parsed(String count) {
		try {
			return Integer.parseInt(count.strip());
		} catch (NumberFormatException e) {
			throw new IllegalArgumentException(PARALLEL_FLAG + " takes a number of repositories, not '"
					+ count.strip() + "'", e);
		}
	}

	private int requireWithinBounds(int count) {
		if (count < BatchAdoption.SEQUENTIAL || count > BatchAdoption.MAX_PARALLELISM) {
			throw new IllegalArgumentException(PARALLEL_FLAG + " must be between " + BatchAdoption.SEQUENTIAL
					+ " and " + BatchAdoption.MAX_PARALLELISM + " but was " + count);
		}
		return count;
	}

	/** What becomes of each repository's checkout once its adoption is over. */
	public CheckoutRetention checkoutRetention() {
		return CheckoutRetention.of(keepWorkspace, dryRun || verifyOnly);
	}

	public Optional<Path> reportFile() {
		return Optional.ofNullable(optionalPath(reportFile));
	}

	/**
	 * The refusal to raise for an argument picocli would not accept. A failure raised
	 * by one of the methods below is already the failure this module means and travels
	 * on unchanged; anything else is the parser's complaint, answered with the usage
	 * line. That complaint quotes the argument it could not place, which may be a
	 * clone URL carrying credentials, so it goes through {@link Redaction} — and the
	 * parser's exception is deliberately <em>not</em> chained, carrying the unmasked
	 * text as its message.
	 */
	private static RuntimeException refusal(ParameterException e) {
		if (e.getCause() instanceof RuntimeException raised) {
			return raised;
		}
		return new IllegalArgumentException(Redaction.of(e.getMessage()) + ". " + USAGE);
	}

	/**
	 * The first positional is always a repository URL, never a workspace, so it is
	 * read as one whatever else is on the command line.
	 */
	@Parameters(index = "0", arity = "0..1", paramLabel = "<github-repo-url>")
	private void positionalRepository(String url) {
		positionalUrl = optionalText(url);
		addRepository(url);
	}

	@Parameters(index = "1", arity = "0..1", paramLabel = "<workspace-directory>")
	private void positionalWorkspace(String value) {
		workspace = optionalPath(value);
	}

	@Parameters(index = "2", arity = "0..1", paramLabel = "<branch-name>")
	private void positionalBranch(String value) {
		branchName = optionalText(value);
	}

	@Option(names = "--repo", paramLabel = "<github-repo-url>")
	private void repository(String url) {
		addFlaggedRepository(url);
	}

	@Option(names = REPOS_FLAG, paramLabel = "<file>")
	private void repositoryList(String file) {
		RepositoryUrls.fromFile(Path.of(Text.required(file, REPOS_FLAG))).forEach(this::addFlaggedRepository);
	}

	@Option(names = "--workspace", paramLabel = "<directory>")
	private void workspace(String value) {
		workspace = optionalPath(value);
	}

	@Option(names = "--branch", paramLabel = "<name>")
	private void branch(String value) {
		branchName = optionalText(value);
	}

	/**
	 * Read as whole minutes rather than an ISO-8601 duration, being set in the units
	 * the operator reasons about — how long a {@code claude init} over their largest
	 * repository takes. Refused before a clone, and bounded by
	 * {@link AdoptionOptions#MAX_TIMEOUT}.
	 */
	@Option(names = TIMEOUT_FLAG, paramLabel = "<minutes>")
	private void timeout(String value) {
		commandTimeout = Text.isPresent(value)
				? Duration.ofMinutes(bounded(TIMEOUT_FLAG, value.strip(), 1, MAX_TIMEOUT_MINUTES, "minutes"))
				: null;
	}

	/**
	 * Zero is meaningful rather than missing — {@code --retries 0} asks for every
	 * failure the moment it happens — so only a blank value falls back to the default.
	 */
	@Option(names = RETRIES_FLAG, paramLabel = "<count>")
	private void retries(String value) {
		retries = Text.isPresent(value)
				? (int) bounded(RETRIES_FLAG, value.strip(), 0, AdoptionOptions.MAX_RETRIES, "attempts")
				: AdoptionOptions.DEFAULT_RETRIES;
	}

	private void addFlaggedRepository(String url) {
		flagsNamedARepository = flagsNamedARepository || Text.isPresent(url);
		addRepository(url);
	}

	/** A blank repository is dropped rather than adopted, as every optional input is. */
	private void addRepository(String url) {
		if (Text.isPresent(url)) {
			repositoryUrls.add(url.strip());
		}
	}

	private static Path optionalPath(String value) {
		String path = optionalText(value);
		return path == null ? null : Path.of(path);
	}

	private static String optionalText(String value) {
		return Text.orDefault(value, null);
	}

	/**
	 * The whole number a counting flag names, refused unless it falls within that
	 * flag's bounds. Both flags read it here, so a bad value is refused in one wording
	 * — naming the flag, what it counts, and the range — followed by the usage line.
	 *
	 * @param unit what the number counts, so the refusal reads as the operator's own
	 *             question rather than as a type error
	 */
	private static long bounded(String flag, String value, long minimum, long maximum, String unit) {
		long parsed = wholeNumber(flag, value, unit);
		if (parsed < minimum || parsed > maximum) {
			throw new IllegalArgumentException(flag + " must be between " + minimum + " and " + maximum + " " + unit
					+ ", but was " + value + ". " + USAGE);
		}
		return parsed;
	}

	private static long wholeNumber(String flag, String value, String unit) {
		try {
			return Long.parseLong(value);
		} catch (NumberFormatException e) {
			throw new IllegalArgumentException(
					flag + " must be a whole number of " + unit + ", but was " + value + ". " + USAGE, e);
		}
	}

	/**
	 * A run asking for the usage line is not asked to adopt anything, so the
	 * repository requirements do not apply: {@code --help} alone would otherwise be
	 * refused with the very line it asked to be shown.
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
	 * Rejects a first positional naming no repository owner when the flags already
	 * named repositories — {@code --repos list.txt /tmp/workspace}, where the operator
	 * meant the workspace. The positional keeps its meaning whatever else is on the
	 * command line, so that path would otherwise be cloned and fail several steps
	 * later. Only worth checking when the flags supplied a repository, since
	 * otherwise the positional has nothing else it could be. It is named through
	 * {@link Redaction}, a URL naming no owner still being able to carry credentials.
	 */
	private void requirePositionalNamesARepository() {
		if (positionalUrl != null && flagsNamedARepository && namesNoOwner(positionalUrl)) {
			throw new IllegalArgumentException("The first argument is read as a repository URL, but "
					+ Redaction.of(positionalUrl) + " names no repository owner. Name the workspace with --workspace,"
					+ " or the repository with --repo. " + USAGE);
		}
	}

	/**
	 * An argument {@link RepositoryUrl} cannot parse names no owner either, so
	 * answering here puts the refusal above in front of the operator rather than a
	 * parse failure naming neither the misread argument nor the flag meant instead.
	 */
	private boolean namesNoOwner(String url) {
		try {
			return RepositoryUrl.of(url).slug().isEmpty();
		} catch (IllegalArgumentException e) {
			return true;
		}
	}
}
