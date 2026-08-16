package io.github.adamw7.tools.adopt;

import java.time.Duration;
import java.util.Optional;

import io.github.adamw7.tools.adopt.command.ProcessCommandRunner;
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
 * than left to the caller's {@link ProcessCommandRunner} because it is part of the
 * same answer: an operator who raises it does so for the run, not for one command.
 *
 * @param pullRequest     the metadata {@link io.github.adamw7.tools.adopt.step.PullRequestStep}
 *                        opens its pull request with, defaulting to the adoption's own
 * @param includeAssets   whether the starter Claude Code configuration assets are
 *                        committed alongside the {@code CLAUDE.md}
 * @param ruleVersion     the released {@code claude-code-enforcer} version to pin
 *                        into an adopted Maven project, or {@code null} to resolve
 *                        the version of the {@code tools} build running the
 *                        adoption — read through {@link #pinnedRuleVersion()},
 *                        which is the accessor that says so in its type
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
 */
public record AdoptionOptions(PullRequestOptions pullRequest, boolean includeAssets, String ruleVersion,
		boolean dryRun, Duration commandTimeout, int retries) {

	/**
	 * Two further attempts, which is what a transport-level hiccup takes: the failure
	 * being waited out is a connection that was refused or reset, and one that has
	 * survived four and twelve seconds of waiting is an outage the operator has to be
	 * told about rather than one more attempt can absorb.
	 */
	public static final int DEFAULT_RETRIES = 2;

	/**
	 * The most an operator may ask for. Bounded rather than merely non-negative
	 * because a run is unattended: every attempt beyond this one waits out
	 * {@link io.github.adamw7.tools.adopt.command.RetryingCommandRunner#MAX_BACKOFF}
	 * before it, so a generous number turns a network that is simply down into a batch
	 * that looks like it is working.
	 */
	public static final int MAX_RETRIES = 10;

	/**
	 * The longest a single command may be given. A day, past which a command that has
	 * not returned is a stuck one rather than a slow adoption, and waiting out the
	 * rest of a budget it is never going to use reports nothing the operator can act
	 * on.
	 *
	 * <p>Stated here rather than at each entry point because it is the same answer for
	 * both: the MCP tool serves its calls inside a long-lived server and the command
	 * line runs an unattended batch, and neither reclaims a command whose budget
	 * outlasts the run that set it.
	 */
	public static final Duration MAX_TIMEOUT = Duration.ofDays(1);

	public AdoptionOptions {
		pullRequest = pullRequest == null ? PullRequestOptions.defaults() : pullRequest;
		ruleVersion = Text.orDefault(ruleVersion, null);
		commandTimeout = commandTimeout == null ? ProcessCommandRunner.DEFAULT_TIMEOUT
				: requireWithinBounds(commandTimeout);
		retries = requireWithinBounds(retries);
	}

	/**
	 * The adoption's own pull request, no assets, published for real, at the default
	 * timeout and retry count.
	 */
	public static AdoptionOptions defaults() {
		return new AdoptionOptions(PullRequestOptions.defaults(), false, null, false, null, DEFAULT_RETRIES);
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
	 * A run is rejected here rather than at the first command it would have killed,
	 * so {@code --timeout 0} fails while the operator is still reading the command
	 * line instead of after a clone — and a timeout past {@link #MAX_TIMEOUT} fails
	 * there too, rather than being honoured by a runner that would then be holding a
	 * command the run cannot outlast.
	 */
	private static Duration requireWithinBounds(Duration commandTimeout) {
		if (commandTimeout.isNegative() || commandTimeout.isZero() || commandTimeout.compareTo(MAX_TIMEOUT) > 0) {
			throw new IllegalArgumentException(
					"commandTimeout must be positive and at most " + MAX_TIMEOUT + " but was " + commandTimeout);
		}
		return commandTimeout;
	}

	/**
	 * Rejected here as well as at each entry point, because this record is what a
	 * caller assembling the pipeline for itself builds — and a retry count read from
	 * somewhere neither the command line nor the MCP tool validated would otherwise
	 * only be noticed by the runner, mid-run.
	 */
	private static int requireWithinBounds(int retries) {
		if (retries < 0 || retries > MAX_RETRIES) {
			throw new IllegalArgumentException(
					"retries must be between 0 and " + MAX_RETRIES + " but was " + retries);
		}
		return retries;
	}
}
