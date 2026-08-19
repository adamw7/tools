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
 * own, so what the parser accepts is declared in one place instead of being
 * spelled out twice. {@code --repo} and {@code --repos} are bound to methods
 * because picocli calls a method option once per occurrence, in the order the
 * operator wrote it: that order is the one thing a batch cannot lose, since a run
 * mixing the two adopts and reports its repositories in the order they were named.
 * The workspace and branch are bound to methods too, because a flag and its
 * positional write the one field and the last one named has to win — which is what
 * {@code setOverwrittenOptionsAllowed} buys, and the same setting that lets the
 * repeatable flags be named again and again.
 *
 * <p>A flag that names something is never followed by another flag, and picocli
 * refuses one that is: {@code --branch --draft} named a branch called
 * {@code --draft}, created it, and pushed it, with the draft silently dropped.
 * {@code --title} and {@code --body} take free-form prose, and prose that merely
 * looks like a flag is still prose — the refusal is for a value that is an option
 * of this command, not for any word opening with dashes.
 *
 * <p>The usage line is written out rather than generated from the declarations
 * below, because picocli orders a generated synopsis by reflecting over the
 * members and the JVM does not promise the order it reports them in. This one is
 * ordered to be read: the positionals first, then the flags that name what to
 * adopt, then the pull request's metadata.
 */
@Command(name = "adopt")
public final class CliArguments {

	static final String USAGE = "Usage: [<github-repo-url>] [workspace-directory] [branch-name]"
			+ " [--repo <github-repo-url>]... [--repos <file>]"
			+ " [--workspace <directory>] [--branch <name>]"
			+ " [--title <title>] [--body <body>] [--reviewer <user>]... [--label <label>]..."
			+ " [--assignee <user>]... [--draft] [--assets] [--rule-version <version>]"
			+ " [--rules <minimal|project>] [--section <heading>]..."
			+ " [--dry-run] [--timeout <minutes>] [--retries <count>] [--report <file>] [--help]";

	static final String HELP_FLAG = "--help";
	static final String HELP_SHORTHAND = "-h";

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
	 * How much of the adopted repository's configuration the guard checks. Named
	 * rather than inferred, and refused when it names neither rule set, because a
	 * misspelt value read as the default would quietly change what somebody else's
	 * build enforces.
	 */
	@Option(names = "--rules", paramLabel = "<minimal|project>")
	private String rules;

	/**
	 * A {@code CLAUDE.md} heading the guard is to demand, repeatable. Naming any
	 * replaces the set the detected build system would have asked for, so a project
	 * whose document is not a Java project's is reshaped to its own headings and
	 * guarded on them.
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
	 * A command line that asked for the usage line is answered with it even when
	 * another of its arguments could not be read. picocli calls an option's method as
	 * it parses, so an unreadable {@code --repos} file or a {@code --timeout} that is
	 * not a number is raised while {@code --help} is still only a flag further along
	 * the list. The refusal is the answer only for a run that was asking to adopt
	 * something.
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
	 * the word somewhere. A help flag written where a value was expected is that
	 * value — {@code --title -h} names a title, badly — so it is read as the argument
	 * the operator got wrong and not as a request. Reading every occurrence as a
	 * request answered {@code --repo <url> --title -h} with the usage line and exited
	 * zero, having adopted nothing.
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
	 * The names of the options that consume the argument after them, read from the
	 * declarations above rather than listed again here, so an option added to this
	 * command cannot swallow a help flag the parse then answers as a request.
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
	 * @return every repository to adopt in this run, in the order the operator named
	 *         them, and without duplicates — the same repository twice would adopt
	 *         one checkout a second time
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
				guardRules(), sections);
	}

	/**
	 * @return the rule set named, or the default when none was. A blank value counts
	 *         as none, so an omitted option and an empty one agree; anything else that
	 *         is not a rule set is refused by {@link GuardRules#of}.
	 */
	private GuardRules guardRules() {
		return rules == null || rules.isBlank() ? GuardRules.PROJECT : GuardRules.of(rules);
	}

	public Optional<Path> reportFile() {
		return Optional.ofNullable(optionalPath(reportFile));
	}

	/**
	 * The refusal to raise for an argument picocli would not accept. A failure raised
	 * by one of the methods below — an unreadable repository list, a bad timeout — is
	 * already the failure this module means, and travels on unchanged. Anything else
	 * is the parser's own complaint, answered with the usage line.
	 *
	 * <p>That complaint quotes the argument it could not place, and one of the
	 * arguments this command takes is a clone URL carrying credentials, so it goes
	 * through {@link Redaction}. The parser's exception is deliberately <em>not</em>
	 * chained: it carries the unmasked text as its message, which the stack trace of
	 * an uncaught refusal would print straight back out.
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
	 * The timeout is read as whole minutes rather than as an ISO-8601 duration,
	 * because it is set in the units the operator is reasoning about — how long a
	 * {@code claude init} over their largest repository takes. It is refused while
	 * they are still reading the command line rather than after a clone, and bounded
	 * by {@link AdoptionOptions#MAX_TIMEOUT} because a batch left running overnight
	 * cannot reclaim a command whose budget outlasts it either.
	 */
	@Option(names = TIMEOUT_FLAG, paramLabel = "<minutes>")
	private void timeout(String value) {
		commandTimeout = Text.isPresent(value)
				? Duration.ofMinutes(bounded(TIMEOUT_FLAG, value.strip(), 1, MAX_TIMEOUT_MINUTES, "minutes"))
				: null;
	}

	/**
	 * Zero is a meaningful answer here rather than a missing one — an operator who
	 * wants every failure reported the moment it happens says so with
	 * {@code --retries 0} — so only a blank value falls back to the default.
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
	 * The whole number a counting flag names, refused unless it falls within the
	 * bounds that flag allows. Both flags that take one are read here rather than each
	 * spelling out its own parse and its own range check, so a value neither of them
	 * accepts is refused in the one wording — naming the flag, what it counts, and the
	 * range — and followed by the usage line, as every other refusal this parser
	 * raises is.
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
	 * Rejects a first positional that names no repository owner when the flags
	 * already named repositories — {@code --repos list.txt /tmp/workspace}, where the
	 * operator meant the workspace the flags left unnamed. The positional keeps its
	 * meaning whatever else is on the command line, so that path would otherwise be
	 * cloned and fail several steps later; saying so here names the argument and the
	 * flag meant instead. The check is only worth making when the flags supplied a
	 * repository, since a run whose only repository is the positional has nothing
	 * else it could be.
	 *
	 * <p>The argument is named through {@link Redaction}: a URL naming no owner can
	 * still carry credentials — {@code https://x-access-token:TOKEN@github.com/repo},
	 * whose userinfo and host collapse into one segment — and this message is the
	 * last thing a run prints before it stops.
	 */
	private void requirePositionalNamesARepository() {
		if (positionalUrl != null && flagsNamedARepository && namesNoOwner(positionalUrl)) {
			throw new IllegalArgumentException("The first argument is read as a repository URL, but "
					+ Redaction.of(positionalUrl) + " names no repository owner. Name the workspace with --workspace,"
					+ " or the repository with --repo. " + USAGE);
		}
	}

	/**
	 * An argument {@link RepositoryUrl} cannot parse at all names no owner either —
	 * and no repository — so answering the question here is what puts the refusal
	 * above in front of the operator, rather than the parse failure's "repositoryUrl
	 * must end in a repository name", which names neither the argument that was
	 * misread nor the flag that was meant.
	 */
	private boolean namesNoOwner(String url) {
		try {
			return RepositoryUrl.of(url).slug().isEmpty();
		} catch (IllegalArgumentException e) {
			return true;
		}
	}
}
