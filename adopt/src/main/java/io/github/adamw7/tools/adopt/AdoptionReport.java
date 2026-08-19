package io.github.adamw7.tools.adopt;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * The machine-readable outcome of an adoption run: the steps that completed, in
 * order, the checkout they were made in, and the pull request's URL.
 * {@link GitHubRepoAdopter#adopt} fills one in and returns it, so callers report
 * what happened without scraping logs. A run that fails part-way is reported too —
 * the completed steps stay recorded and the failing step's message becomes the run's
 * failure, so an abandoned adoption cannot look like a short successful one.
 */
public final class AdoptionReport {

	private final List<String> completedSteps = new ArrayList<>();
	private String checkout;
	private String pullRequestUrl;
	private String failure;

	public void recordStep(String name) {
		completedSteps.add(name);
	}

	/** @param directory the checkout the adoption works in, as an absolute path */
	public void recordCheckout(String directory) {
		this.checkout = directory;
	}

	public void recordPullRequestUrl(String url) {
		this.pullRequestUrl = url;
	}

	public void recordFailure(String message) {
		this.failure = message;
	}

	public List<String> completedSteps() {
		return List.copyOf(completedSteps);
	}

	/**
	 * @return the directory the adoption's checkout was made in, or empty when the run
	 *         stopped before it claimed one. It is the run's one output that does not
	 *         reach GitHub, so a dry run — which pushes nothing and opens no pull
	 *         request — has nothing else to point a caller at, and a caller that named
	 *         no workspace could not otherwise find the temporary one it was given.
	 */
	public Optional<String> checkout() {
		return Optional.ofNullable(checkout);
	}

	public Optional<String> pullRequestUrl() {
		return Optional.ofNullable(pullRequestUrl);
	}

	/**
	 * @return why the run stopped, or empty when every configured step completed
	 */
	public Optional<String> failure() {
		return Optional.ofNullable(failure);
	}

	public boolean succeeded() {
		return failure == null;
	}
}
