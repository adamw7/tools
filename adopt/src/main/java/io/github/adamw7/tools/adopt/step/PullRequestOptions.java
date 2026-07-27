package io.github.adamw7.tools.adopt.step;

import java.util.List;

import io.github.adamw7.tools.adopt.Text;

/**
 * The metadata a {@link PullRequestStep} opens its pull request with: the title
 * and body, plus optional reviewers, labels, and assignees, and whether the pull
 * request is opened as a draft. Grouping them keeps the step from a telescoping
 * constructor.
 *
 * <p>A title or body that is absent or blank falls back to the adoption default,
 * and the lists are defensively copied and never {@code null}, so the command line
 * and the MCP tool map their arguments straight in and no {@code null} can reach
 * {@code gh}'s arguments.
 */
public record PullRequestOptions(String title, String body, List<String> reviewers, List<String> labels,
		List<String> assignees, boolean draft) {

	static final String DEFAULT_TITLE = "Adopt Claude Code";
	static final String DEFAULT_BODY = "Adds a generated CLAUDE.md and wires the CLAUDE.md guard "
			+ "into the build so the file keeps being validated.";

	public PullRequestOptions {
		title = orDefault(title, DEFAULT_TITLE);
		body = orDefault(body, DEFAULT_BODY);
		reviewers = List.copyOf(reviewers);
		labels = List.copyOf(labels);
		assignees = List.copyOf(assignees);
	}

	/** The adoption's own title and body, requesting nobody and not a draft. */
	public static PullRequestOptions defaults() {
		return new PullRequestOptions(null, null, List.of(), List.of(), List.of(), false);
	}

	private static String orDefault(String value, String fallback) {
		return Text.isPresent(value) ? value.strip() : fallback;
	}
}
