package io.github.adamw7.tools.adopt;

import java.util.Optional;

/**
 * One repository's adoption within a run: the inputs it was given and the report
 * it produced. A batch answers with a list of these rather than a list of bare
 * reports, because a report on its own does not say which repository it belongs
 * to — and a run over several repositories is read repository by repository.
 *
 * @param context the inputs the adoption was run with
 * @param report  what the adoption achieved, and why it stopped when it did
 */
public record AdoptionRun(AdoptionContext context, AdoptionReport report) {

	public String repositoryUrl() {
		return context.repositoryUrl();
	}

	public boolean succeeded() {
		return report.succeeded();
	}

	/** @return why this repository's adoption stopped, or empty when it completed */
	public Optional<String> failure() {
		return report.failure();
	}
}
