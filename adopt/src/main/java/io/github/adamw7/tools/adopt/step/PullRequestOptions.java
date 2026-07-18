package io.github.adamw7.tools.adopt.step;

import java.util.List;

/**
 * The metadata a {@link PullRequestStep} opens its pull request with: the title
 * and body, plus optional reviewers, labels, and assignees to request, and
 * whether the pull request is opened as a draft. Grouping them keeps the step
 * from a telescoping constructor and lets callers set only the fields they care
 * about through {@link #builder()}.
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
		title = requireText(title, "title");
		body = requireText(body, "body");
		reviewers = List.copyOf(reviewers);
		labels = List.copyOf(labels);
		assignees = List.copyOf(assignees);
	}

	public static PullRequestOptions defaults() {
		return builder().build();
	}

	public static Builder builder() {
		return new Builder();
	}

	private static String requireText(String value, String field) {
		if (value == null || value.isBlank()) {
			throw new IllegalArgumentException(field + " must not be blank");
		}
		return value.strip();
	}

	/** Fluent builder that starts from the adoption defaults and no reviewers, labels, or assignees. */
	public static final class Builder {

		private String title = DEFAULT_TITLE;
		private String body = DEFAULT_BODY;
		private List<String> reviewers = List.of();
		private List<String> labels = List.of();
		private List<String> assignees = List.of();
		private boolean draft;

		private Builder() {
		}

		public Builder title(String title) {
			this.title = title;
			return this;
		}

		public Builder body(String body) {
			this.body = body;
			return this;
		}

		public Builder reviewers(List<String> reviewers) {
			this.reviewers = reviewers;
			return this;
		}

		public Builder labels(List<String> labels) {
			this.labels = labels;
			return this;
		}

		public Builder assignees(List<String> assignees) {
			this.assignees = assignees;
			return this;
		}

		public Builder draft(boolean draft) {
			this.draft = draft;
			return this;
		}

		public PullRequestOptions build() {
			return new PullRequestOptions(title, body, reviewers, labels, assignees, draft);
		}
	}
}
