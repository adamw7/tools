package io.github.adamw7.tools.adopt;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

/**
 * Gives every repository of a run its own checkout: one workspace, one branch
 * name, and a directory no other repository of the run has claimed. Both entry
 * points come through here, so the command line and the MCP tool cannot drift
 * apart on how a run's contexts are made.
 *
 * <p>Two repositories that would clone into the same checkout directory are
 * rejected rather than adopted. A checkout is named after the repository alone, so
 * {@code owner/tools} and {@code other-owner/tools} claim one directory, as do a
 * repository's {@code .../repo} and {@code .../repo.git} forms. The second
 * adoption would otherwise run every step against the first repository's working
 * tree and report its pull request as the second's.
 *
 * <p>Contexts are claimed one repository at a time rather than built for the whole
 * run up front, because both failures a claim can raise — a URL that names no
 * repository, and a checkout directory already taken — belong to the repository
 * that raised them. Building them all up front made either one abort the batch
 * before its first clone and leave the run with no report at all, which is exactly
 * what {@link BatchAdoption} keeps from happening for every failure the pipeline
 * itself raises.
 */
public final class Checkouts {

	private final Path workspace;
	private final String branchName;

	/** The checkout each repository of the run has taken, so a second claim on one is caught. */
	private final Map<Path, String> urlsByCheckout = new HashMap<>();

	/**
	 * @param workspace  the directory every clone is created under
	 * @param branchName the feature branch every adoption commits on
	 */
	public Checkouts(Path workspace, String branchName) {
		this.workspace = workspace;
		this.branchName = branchName;
	}

	/** The branch every repository of this run is adopted on. */
	public String branchName() {
		return branchName;
	}

	/**
	 * Claims this run's checkout directory for one repository.
	 *
	 * @return the context to adopt it with
	 * @throws IllegalArgumentException when the URL names no repository, or when
	 *                                  another repository of the run already claimed
	 *                                  the checkout directory it would clone into
	 */
	public AdoptionContext claim(String repositoryUrl) {
		AdoptionContext context = new AdoptionContext(repositoryUrl, workspace, branchName);
		requireCheckoutUnclaimed(context);
		return context;
	}

	private void requireCheckoutUnclaimed(AdoptionContext context) {
		String claimedBy = urlsByCheckout.putIfAbsent(context.repositoryDirectory(), context.displayUrl());
		if (claimedBy != null) {
			throw new IllegalArgumentException("Cannot adopt " + context.displayUrl() + " and " + claimedBy
					+ " in one run: both would clone into " + context.repositoryDirectory()
					+ ". Adopt them in separate runs, or into separate workspaces.");
		}
	}
}
