package io.github.adamw7.tools.adopt;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Gives every repository of a run its own checkout, as the {@link AdoptionContext}
 * list the run works through: one workspace, one branch name, in the order the
 * URLs were given. Both entry points come through here, so the command line and
 * the MCP tool cannot drift apart on how a run's contexts are made.
 *
 * <p>Two repositories that would clone into the same checkout directory are
 * rejected rather than adopted. A checkout is named after the repository alone, so
 * {@code owner/tools} and {@code other-owner/tools} claim one directory, as do a
 * repository's {@code .../repo} and {@code .../repo.git} forms. The second
 * adoption would otherwise run every step against the first repository's working
 * tree and report its pull request as the second's.
 */
public final class Checkouts {

	private Checkouts() {
	}

	/**
	 * @param repositoryUrls the repositories to adopt, without duplicates
	 * @param workspace      the directory every clone is created under
	 * @param branchName     the feature branch every adoption commits on
	 * @throws IllegalArgumentException when two repositories would clone into the
	 *                                  same checkout directory
	 */
	public static List<AdoptionContext> forRun(List<String> repositoryUrls, Path workspace, String branchName) {
		List<AdoptionContext> contexts = repositoryUrls.stream()
				.map(url -> new AdoptionContext(url, workspace, branchName))
				.toList();
		requireDistinctCheckouts(contexts);
		return contexts;
	}

	private static void requireDistinctCheckouts(List<AdoptionContext> contexts) {
		Map<Path, String> urlsByCheckout = new HashMap<>();
		contexts.forEach(context -> requireCheckoutUnclaimed(urlsByCheckout, context));
	}

	private static void requireCheckoutUnclaimed(Map<Path, String> urlsByCheckout, AdoptionContext context) {
		String claimedBy = urlsByCheckout.putIfAbsent(context.repositoryDirectory(), context.repositoryUrl());
		if (claimedBy != null) {
			throw new IllegalArgumentException("Cannot adopt " + context.repositoryUrl() + " and " + claimedBy
					+ " in one run: both would clone into " + context.repositoryDirectory()
					+ ". Adopt them in separate runs, or into separate workspaces.");
		}
	}
}
