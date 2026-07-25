package io.github.adamw7.tools.adopt;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * The machine-readable outcome of an adoption run: the steps that completed, in
 * order, and the URL of the pull request the run opened (or found already open).
 * {@link GitHubRepoAdopter#adopt} fills one in and returns it, so callers — the
 * command line, the MCP server, or any embedding code — can report what happened
 * without scraping logs. The pull-request URL is absent until a step records it,
 * for example when the pipeline is configured without a pull-request step.
 *
 * <p>A report is also filled in for a run that fails part-way: the steps that did
 * complete stay recorded and the failing step's message is recorded as the run's
 * failure, so the report distinguishes a completed adoption from an abandoned one
 * instead of looking like a short but successful run.
 */
public final class AdoptionReport {

	private final List<String> completedSteps = new ArrayList<>();
	private String pullRequestUrl;
	private String failure;

	public void recordStep(String name) {
		completedSteps.add(name);
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
