package io.github.adamw7.tools.adopt;

import java.time.Duration;
import java.util.List;
import java.util.Optional;

import io.github.adamw7.tools.adopt.command.ProcessCommandRunner;
import io.github.adamw7.tools.adopt.step.GuardOptions;
import io.github.adamw7.tools.adopt.step.GuardRules;
import io.github.adamw7.tools.adopt.step.PullRequestOptions;

/**
 * How one adoption run is configured: the metadata its pull request carries,
 * whether the starter assets are committed, which {@code claude-code-enforcer}
 * version a Maven project pins, whether the run publishes anything at all, and how
 * long a single external command may take. Grouping them keeps
 * {@link GitHubRepoAdopter#withDefaultPipeline} and the MCP tool's pipeline seam
 * from growing a parameter every time the pipeline gains a switch — the same
 * reason {@link PullRequestOptions} exists one level down.
 *
 * <p>Both entry points build one of these, so the command line and the MCP tool
 * cannot drift apart on what an omitted option means. The timeout is here rather
 * than on the caller's {@link ProcessCommandRunner} because an operator who raises
 * it does so for the run, not for one command.
 *
 * @param pullRequest     the metadata {@link io.github.adamw7.tools.adopt.step.PullRequestStep}
 *                        opens its pull request with, defaulting to the adoption's own
 * @param includeAssets   whether the starter Claude Code configuration assets are
 *                        committed alongside the {@code CLAUDE.md}
 * @param ruleVersion     the released {@code claude-code-enforcer} version to pin
 *                        into an adopted Maven project, or {@code null} to resolve
 *                        the running build's — read through
 *                        {@link #pinnedRuleVersion()}, which says so in its type
 * @param dryRun          whether the run stops after the verification, committing
 *                        the adoption locally but pushing nothing and opening no
 *                        pull request
 * @param commandTimeout  how long any one external command may run before it is
 *                        destroyed, defaulting to
 *                        {@link ProcessCommandRunner#DEFAULT_TIMEOUT} and bounded
 *                        by {@link #MAX_TIMEOUT}
 * @param retries         how many further attempts a {@code git} or {@code gh}
 *                        command the network refused earns, defaulting to
 *                        {@link #DEFAULT_RETRIES} and bounded by
 *                        {@link #MAX_RETRIES}; zero adopts exactly as an
 *                        undecorated run does
 * @param guardRules      how much of the adopted repository's configuration the
 *                        wired guard checks, defaulting to all of it: the adoption
 *                        installs an {@code AGENTS.md} and, with {@code --assets},
 *                        a {@code .claude} directory, so a {@code CLAUDE.md}-only
 *                        guard left what the run wrote unchecked
 * @param claudeMdSections the headings the guard demands and the reshape conforms
 *                        to, empty to use the ones the detected build system asks
 *                        for
 * @param verifyOnly      whether the run only reports whether each repository is
 *                        still adopted and its guard still passes, cloning and
 *                        reading but writing nothing at all
 */
public record AdoptionOptions(PullRequestOptions pullRequest, boolean includeAssets, String ruleVersion,
		boolean dryRun, Duration commandTimeout, int retries, GuardRules guardRules,
		List<String> claudeMdSections, boolean verifyOnly) {

	/**
	 * Two further attempts, what a transport-level hiccup takes. A connection still
	 * refused after four and twelve seconds of waiting is an outage to report rather
	 * than one more attempt can absorb.
	 */
	public static final int DEFAULT_RETRIES = 2;

	/**
	 * The most an operator may ask for. Bounded rather than merely non-negative
	 * because a run is unattended: every further attempt waits out
	 * {@link io.github.adamw7.tools.adopt.command.RetryingCommandRunner#MAX_BACKOFF},
	 * so a generous number turns a network that is down into a batch that looks busy.
	 */
	public static final int MAX_RETRIES = 10;

	/**
	 * The longest a single command may be given: a day, past which a command that has
	 * not returned is stuck rather than slow. Stated here rather than at each entry
	 * point, being the same answer for both — neither the MCP server nor an unattended
	 * batch reclaims a command whose budget outlasts the run that set it.
	 */
	public static final Duration MAX_TIMEOUT = Duration.ofDays(1);

	public AdoptionOptions {
		pullRequest = pullRequest == null ? PullRequestOptions.defaults() : pullRequest;
		guardRules = guardRules == null ? GuardRules.PROJECT : guardRules;
		claudeMdSections = claudeMdSections == null ? List.of() : List.copyOf(claudeMdSections);
		ruleVersion = Text.orDefault(ruleVersion, null);
		commandTimeout = commandTimeout == null ? ProcessCommandRunner.DEFAULT_TIMEOUT
				: requireWithinBounds(commandTimeout);
		retries = requireWithinBounds(retries);
	}

	/**
	 * The options without a guard named: the whole configuration is checked, on the
	 * sections the detected build system asks for.
	 */
	public AdoptionOptions(PullRequestOptions pullRequest, boolean includeAssets, String ruleVersion,
			boolean dryRun, Duration commandTimeout, int retries) {
		this(pullRequest, includeAssets, ruleVersion, dryRun, commandTimeout, retries, GuardRules.PROJECT,
				List.of(), false);
	}

	/** The options without a verification asked for, which is every adoption. */
	public AdoptionOptions(PullRequestOptions pullRequest, boolean includeAssets, String ruleVersion,
			boolean dryRun, Duration commandTimeout, int retries, GuardRules guardRules,
			List<String> claudeMdSections) {
		this(pullRequest, includeAssets, ruleVersion, dryRun, commandTimeout, retries, guardRules,
				claudeMdSections, false);
	}

	/**
	 * The adoption's own pull request, no assets, published for real, at the default
	 * timeout and retry count.
	 */
	public static AdoptionOptions defaults() {
		return new AdoptionOptions(PullRequestOptions.defaults(), false, null, false, null, DEFAULT_RETRIES,
				GuardRules.PROJECT, List.of(), false);
	}

	/**
	 * What the guard wired into an adopted project is made of. The pipeline is
	 * assembled from this rather than from the three fields separately, so the reshape
	 * and the guard it must satisfy cannot be built from different answers.
	 */
	public GuardOptions guard() {
		return new GuardOptions(ruleVersion, guardRules, claudeMdSections);
	}

	/**
	 * @return the released rule version to pin, or empty to resolve the version of
	 *         the {@code tools} build running the adoption. Preferred over the
	 *         record's own {@link #ruleVersion()}, which answers {@code null} for
	 *         the same case.
	 */
	public Optional<String> pinnedRuleVersion() {
		return Optional.ofNullable(ruleVersion);
	}

	/**
	 * Rejected here rather than at the first command it would have killed, so
	 * {@code --timeout 0} fails before a clone — as does one past {@link #MAX_TIMEOUT},
	 * rather than leaving a runner holding a command the run cannot outlast.
	 */
	private static Duration requireWithinBounds(Duration commandTimeout) {
		if (commandTimeout.isNegative() || commandTimeout.isZero() || commandTimeout.compareTo(MAX_TIMEOUT) > 0) {
			throw new IllegalArgumentException(
					"commandTimeout must be positive and at most " + MAX_TIMEOUT + " but was " + commandTimeout);
		}
		return commandTimeout;
	}

	/**
	 * Rejected here as well as at each entry point, this record being what a caller
	 * assembling its own pipeline builds: a count neither entry point validated would
	 * otherwise only be noticed by the runner, mid-run.
	 */
	private static int requireWithinBounds(int retries) {
		if (retries < 0 || retries > MAX_RETRIES) {
			throw new IllegalArgumentException(
					"retries must be between 0 and " + MAX_RETRIES + " but was " + retries);
		}
		return retries;
	}
}
