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
 *
 * <p>Each list entry is stripped, and a blank one is dropped rather than carried:
 * every entry becomes an argument of its own — {@code gh pr create --reviewer
 * <entry>} — so a blank one reached {@code gh} as an empty argument and failed the
 * adoption at its very last step, with the branch already pushed. The MCP tool
 * already read its arguments this way; doing it here rather than at each entry
 * point is what keeps the command line from disagreeing with it about what an
 * omitted value means. Duplicates are left alone, as {@code gh} treats naming a
 * reviewer twice as naming them once.
 *
 * @param title     the pull request's title, or blank for {@value #DEFAULT_TITLE}
 * @param body      the pull request's body, or blank for the adoption's own
 * @param reviewers the users to request a review from, empty to request nobody
 * @param labels    the labels to apply, empty to apply none
 * @param assignees the users to assign, empty to assign nobody
 * @param draft     whether the pull request is opened as a draft
 */
public record PullRequestOptions(String title, String body, List<String> reviewers, List<String> labels,
		List<String> assignees, boolean draft) {

	static final String DEFAULT_TITLE = "Adopt Claude Code";
	static final String DEFAULT_BODY = "Adds a generated CLAUDE.md and wires the CLAUDE.md guard "
			+ "into the build so the file keeps being validated.";

	public PullRequestOptions {
		title = Text.orDefault(title, DEFAULT_TITLE);
		body = Text.orDefault(body, DEFAULT_BODY);
		reviewers = supplied(reviewers);
		labels = supplied(labels);
		assignees = supplied(assignees);
	}

	/**
	 * An absent list is read as naming nobody, the same answer a blank title gets from
	 * {@link Text#orDefault}: this is the record's promise that its lists are never
	 * {@code null}, so it cannot be the one place a caller has to satisfy it first. A
	 * {@code null} reached here as a message-less {@link NullPointerException} out of a
	 * constructor whose documentation says the lists are defensively copied.
	 *
	 * @return the entries that name something, stripped — the rule {@link Text} defines
	 *         for every optional input
	 */
	private static List<String> supplied(List<String> values) {
		if (values == null) {
			return List.of();
		}
		return values.stream().filter(Text::isPresent).map(String::strip).toList();
	}

	/** The adoption's own title and body, requesting nobody and not a draft. */
	public static PullRequestOptions defaults() {
		return new PullRequestOptions(null, null, List.of(), List.of(), List.of(), false);
	}
}
