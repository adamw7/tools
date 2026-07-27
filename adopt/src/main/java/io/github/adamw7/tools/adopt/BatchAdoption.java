package io.github.adamw7.tools.adopt;

import java.util.List;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Adopts a list of repositories one after another, with a fresh
 * {@link AdoptionReport} for each.
 *
 * <p>A repository whose adoption fails does not stop the batch. The adoptions are
 * independent — an expired {@code gh} login or a missing build tool says nothing
 * about the next repository — so the failure is recorded in that repository's
 * report and the run moves on, leaving the caller to decide what a partly failed
 * batch means. Repositories are adopted sequentially because every adoption
 * shells out to {@code git}, {@code claude}, and {@code gh}, whose output a
 * parallel batch would interleave.
 *
 * <p>Claiming a repository's checkout is part of its own adoption rather than a
 * preparation the whole run shares, so a URL that names no repository, or one
 * whose checkout directory another repository of the run already claimed, is that
 * repository's recorded failure and not the batch's. Otherwise a single malformed
 * line in a {@code --repos} file stopped every repository behind it before the
 * first clone, and left the run with no report to say so.
 */
public final class BatchAdoption {

	/**
	 * A single repository's adoption, filling in the report it is handed. The report
	 * is a parameter rather than a return value so a failed adoption still yields one:
	 * a report only handed back on the return path is lost the moment the adoption
	 * throws, which is when the batch most needs to know how far that repository got.
	 */
	public interface Adoption {
		void adopt(AdoptionContext context, AdoptionReport report);
	}

	private static final Logger log = LogManager.getLogger(BatchAdoption.class);

	private final Adoption adoption;

	public BatchAdoption(Adoption adoption) {
		this.adoption = adoption;
	}

	/**
	 * @param repositoryUrls the repositories to adopt, in the order they were given
	 * @param checkouts      this run's workspace and branch, giving each repository
	 *                       its own checkout directory
	 * @return one run per repository, in the order the repositories were given
	 */
	public List<AdoptionRun> adoptAll(List<String> repositoryUrls, Checkouts checkouts) {
		log.info("Adopting Claude Code into {} repositories", repositoryUrls.size());
		return repositoryUrls.stream().map(url -> adoptOne(url, checkouts)).toList();
	}

	/**
	 * The URL is redacted up front so a repository that fails its claim — and so
	 * never has a context to ask — is still reported without its credentials.
	 */
	private AdoptionRun adoptOne(String repositoryUrl, Checkouts checkouts) {
		AdoptionReport report = new AdoptionReport();
		String displayUrl = Redaction.of(repositoryUrl);
		try {
			adoption.adopt(checkouts.claim(repositoryUrl), report);
		} catch (RuntimeException e) {
			recordFailure(displayUrl, report, e);
		}
		return new AdoptionRun(displayUrl, checkouts.branchName(), report);
	}

	/**
	 * The failure is logged with its stack trace here because it is not rethrown:
	 * swallowing it to keep the batch going would otherwise be the last that is seen
	 * of it. A failure the pipeline already described — naming the step that raised
	 * it — is left alone, since that reads better than the bare message.
	 */
	private void recordFailure(String displayUrl, AdoptionReport report, RuntimeException failure) {
		log.error("Adoption failed for {}", displayUrl, failure);
		if (report.failure().isEmpty()) {
			report.recordFailure(Failures.describe(failure));
		}
	}
}
