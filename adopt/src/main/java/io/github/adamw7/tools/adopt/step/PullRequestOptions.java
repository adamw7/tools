package io.github.adamw7.tools.adopt.step;

import java.util.List;

import io.github.adamw7.tools.adopt.Text;

/**
 * The metadata a {@link PullRequestStep} opens its pull request with: the title
 * and body, plus optional reviewers, labels, and assignees to request, and
 * whether the pull request is opened as a draft. Grouping them keeps the step
 * from a telescoping constructor.
 *
 * <p>A title or body that is absent or blank falls back to the adoption default —
 * the rule every optional input of the adoption follows — so the command line and
 * the MCP tool map their arguments straight in rather than each guarding an
 * omitted flag for itself, and no {@code null} can reach {@code gh}'s arguments.
 *
 * <p>The lists are defensively copied and never {@code null}, so the step can
 * translate each entry into a repeated {@code gh pr create} flag without
 * guarding against absence.
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
