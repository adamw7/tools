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

import io.github.adamw7.tools.adopt.step.PullRequestOptions;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;
import picocli.CommandLine.ParameterException;

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
 * line and so is answered with it instead, whatever else on the command line
 * could not be read. What it is not read as is a value: a {@code --title -h} is a
 * flag missing its value, and is refused as one.
 *
 * <p>The arguments are matched by picocli rather than by a loop of this module's
 * own: the option names, their values, the three positional slots and the
 * refusals are all declared, so what the parser accepts is readable in one place
 * instead of being spelled out twice — once in the loop and once in the usage
 * line. Each option is bound to a method rather than to a field, because picocli
 * calls a method option once per occurrence, in the order the operator wrote it.
 * That order is the one thing a batch cannot lose: a run mixing {@code --repo}
 * with {@code --repos} adopts its repositories, and reports them, in the order
 * they were named.
 *
 * <p>The positional slots keep their meaning whatever else is on the command line
 * — the first is always a repository URL, never a workspace — so a run driven
 * entirely by {@code --repo}/{@code --repos} names its workspace and branch with
 * {@code --workspace} and {@code --branch}. A flag and its positional write the
 * same value, so naming one twice is the last one winning rather than an error:
 * that is what {@code setOverwrittenOptionsAllowed} buys, and it is the same
 * setting that lets the repeatable flags be named again and again.
 *
 * <p>A flag that names something is never followed by another flag, and picocli
 * refuses one that is: {@code --branch --draft} named a branch called
 * {@code --draft}, created it, and pushed it, with the draft the operator asked
 * for silently dropped. {@code --title} and {@code --body} take free-form prose,
 * and prose that merely looks like a flag is still prose — the refusal is for a
 * value that is an option of this command, not for any word opening with dashes.
 *
 * <p>The usage line is written out rather than generated from the declarations
 * below. picocli can render a synopsis, but it orders one by reflecting over the
 * methods, and the JVM does not promise the order it reports them in — the line
 * an operator reads would then differ between runs. This one is ordered to be
 * read: the positionals first, then the flags that name what to adopt, then the
 * pull request's metadata.
 */
@Command(name = "adopt")
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
	private static final String REPOS_FLAG = "--repos";

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
	 * it parses, so an unreadable {@code --repos} file, a {@code --timeout} that is not
	 * a number, or a misspelled flag is raised while {@code --help} is still only a flag
	 * further along the list — and {@code --help --repos missing.txt} was refused for the
	 * file rather than answered with the line it asked for. The refusal is the answer
	 * only for a run that was asking to adopt something.
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
	 * the operator got wrong and not as a request.
	 *
	 * <p>Reading every occurrence as a request answered {@code --repo <url> --title -h}
	 * with the usage line and exited zero, having adopted nothing: a scripted batch that
	 * mistyped one flag reported success and left no report to say otherwise. It is the
	 * one shape this method has to tell apart, because it is also the one picocli
	 * refuses on purpose — a flag that names something is never followed by another
	 * flag.
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
	 * declarations below rather than listed again here, so an option added to this
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
	 *         them — the positional and the flags interleaved as they were written,
	 *         which is the order the options are bound to methods to preserve — and
	 *         without duplicates, since the same repository twice would adopt one
	 *         checkout a second time
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
	 * The refusal to raise for an argument picocli would not accept. A failure
	 * raised by one of the methods below — an unreadable repository list, a blank
	 * flag value — is already the failure this module means, and picocli wraps it
	 * only because it was the one calling; it travels on unchanged so a caller
	 * still sees an {@link AdoptionException} for a file it could not read. Anything
	 * else is the parser's own complaint about the command line, which is answered
	 * with the usage line as every other bad argument is.
	 *
	 * <p>The parser's complaint quotes the argument it could not place, and one of the
	 * arguments this command takes is a clone URL carrying credentials: a fourth
	 * positional — an operator writing two repositories where the slots hold one — was
	 * refused with {@code Unmatched argument at index 3:
	 * 'https://x-access-token:TOKEN@github.com/owner/repo.git'}, which is the last thing
	 * the run prints. It goes through {@link Redaction}, as every other message the
	 * adoption raises about a URL does. The parser's own exception is deliberately
	 * <em>not</em> chained: it carries the unmasked text as its message, which the
	 * stack trace of an uncaught refusal would print straight back out.
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

	@Option(names = "--title", paramLabel = "<title>")
	private void title(String value) {
		title = value;
	}

	@Option(names = "--body", paramLabel = "<body>")
	private void body(String value) {
		body = value;
	}

	@Option(names = "--reviewer", paramLabel = "<user>")
	private void reviewer(String value) {
		reviewers.add(value);
	}

	@Option(names = "--label", paramLabel = "<label>")
	private void label(String value) {
		labels.add(value);
	}

	@Option(names = "--assignee", paramLabel = "<user>")
	private void assignee(String value) {
		assignees.add(value);
	}

	@Option(names = "--rule-version", paramLabel = "<version>")
	private void ruleVersion(String value) {
		ruleVersion = optionalText(value);
	}

	@Option(names = TIMEOUT_FLAG, paramLabel = "<minutes>")
	private void timeout(String value) {
		commandTimeout = optionalTimeout(value);
	}

	@Option(names = "--report", paramLabel = "<file>")
	private void report(String value) {
		reportFile = optionalPath(value);
	}

	@Option(names = "--draft")
	private void draft(boolean on) {
		draft = on;
	}

	@Option(names = "--assets")
	private void assets(boolean on) {
		assets = on;
	}

	@Option(names = "--dry-run")
	private void dryRun(boolean on) {
		dryRun = on;
	}

	@Option(names = { HELP_FLAG, HELP_SHORTHAND })
	private void help(boolean on) {
		help = on;
	}

	private void addFlaggedRepository(String url) {
		flagsNamedARepository = flagsNamedARepository || Text.isPresent(url);
		addRepository(url);
	}

	/**
	 * A blank repository is dropped rather than adopted — the rule {@link Text}
	 * defines for every optional input, and the one every optional flag value
	 * follows too — leaving the run with whatever the other arguments named.
	 */
	private void addRepository(String url) {
		if (Text.isPresent(url)) {
			repositoryUrls.add(url.strip());
		}
	}

	private Path optionalPath(String value) {
		String path = optionalText(value);
		return path == null ? null : Path.of(path);
	}

	private String optionalText(String value) {
		return Text.orDefault(value, null);
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

	/**
	 * An argument {@link RepositoryUrl} cannot parse at all names no owner either —
	 * and no repository, so no adoption was ever going to be made of it whichever
	 * argument the operator meant it to be. Answering the question rather than
	 * letting the parse failure out is what puts the refusal above in front of them:
	 * a {@code /tmp/workspace//} whose trailing slashes leave no last segment, or a
	 * {@code C:\workspaces\ws} on Windows, was refused with the parser's own
	 * "repositoryUrl must end in a repository name" — which names neither the
	 * argument that was misread nor the flag that was meant.
	 */
	private boolean namesNoOwner(String url) {
		try {
			return RepositoryUrl.of(url).slug().isEmpty();
		} catch (IllegalArgumentException e) {
			return true;
		}
	}
}
