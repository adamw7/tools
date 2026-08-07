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
 *                        {@link ProcessCommandRunner#DEFAULT_TIMEOUT}
 */
public record AdoptionOptions(PullRequestOptions pullRequest, boolean includeAssets, String ruleVersion,
		boolean dryRun, Duration commandTimeout) {

	public AdoptionOptions {
		pullRequest = pullRequest == null ? PullRequestOptions.defaults() : pullRequest;
		ruleVersion = Text.orDefault(ruleVersion, null);
		commandTimeout = commandTimeout == null ? ProcessCommandRunner.DEFAULT_TIMEOUT : requirePositive(commandTimeout);
	}

	/** The adoption's own pull request, no assets, published for real, at the default timeout. */
	public static AdoptionOptions defaults() {
		return new AdoptionOptions(PullRequestOptions.defaults(), false, null, false, null);
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
	 * line instead of after a clone.
	 */
	private static Duration requirePositive(Duration commandTimeout) {
		if (commandTimeout.isNegative() || commandTimeout.isZero()) {
			throw new IllegalArgumentException("commandTimeout must be positive but was " + commandTimeout);
		}
		return commandTimeout;
	}
}
