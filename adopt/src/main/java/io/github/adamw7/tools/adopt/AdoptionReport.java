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
 */
public final class AdoptionReport {

	private final List<String> completedSteps = new ArrayList<>();
	private String pullRequestUrl;

	public void recordStep(String name) {
		completedSteps.add(name);
	}

	public void recordPullRequestUrl(String url) {
		this.pullRequestUrl = url;
	}

	public List<String> completedSteps() {
		return List.copyOf(completedSteps);
	}

	public Optional<String> pullRequestUrl() {
		return Optional.ofNullable(pullRequestUrl);
	}
}
