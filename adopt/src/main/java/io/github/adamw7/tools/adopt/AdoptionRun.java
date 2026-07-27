package io.github.adamw7.tools.adopt;

import java.util.Optional;

/**
 * One repository's adoption within a run: what it was asked to do and the report
 * it produced. A batch answers with a list of these rather than a list of bare
 * reports, because a report on its own does not say which repository it belongs
 * to — and a run over several repositories is read repository by repository.
 *
 * <p>The repository and branch are held as text rather than as the
 * {@link AdoptionContext} they came from, because a repository whose URL names no
 * repository at all never gets a context and still has to be reported: surviving
 * that is the whole point of {@link BatchAdoption}.
 *
 * @param repositoryUrl the repository that was adopted, with any clone credentials
 *                      masked — a run is a reporting artifact, written to the
 *                      report file and answered to MCP clients
 * @param branchName    the feature branch the adoption was to be made on
 * @param report        what the adoption achieved, and why it stopped when it did
 */
public record AdoptionRun(String repositoryUrl, String branchName, AdoptionReport report) {

	public boolean succeeded() {
		return report.succeeded();
	}

	/** @return why this repository's adoption stopped, or empty when it completed */
	public Optional<String> failure() {
		return report.failure();
	}
}
