package io.github.adamw7.tools.adopt;

import java.util.List;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Adopts a list of repositories, one after another, with a fresh
 * {@link AdoptionReport} for each. A batch is why the adoption takes a list of
 * URLs at all: a team adopts Claude Code across its repositories in one run
 * rather than invoking the pipeline once per repository by hand.
 *
 * <p>A repository whose adoption fails does not stop the batch. Failing fast
 * would leave the repositories behind it untried, and their adoptions are
 * independent — a {@code gh} login that expired for one organisation, or a project
 * whose build tool is missing, says nothing about the next repository on the
 * list. The failure is recorded in that repository's report and the run moves on,
 * so the caller can see exactly which repositories were adopted and which were
 * not; deciding what a partly failed batch means — a non-zero exit, an error
 * result — is the caller's, not the batch's.
 *
 * <p>Repositories are adopted sequentially rather than in parallel: every
 * adoption shells out to {@code git}, {@code claude}, and {@code gh}, and a
 * parallel batch would interleave their output and multiply the load a single
 * {@code claude init} already puts on the machine.
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

	/** @return one run per repository, in the order the repositories were given */
	public List<AdoptionRun> adoptAll(List<AdoptionContext> contexts) {
		log.info("Adopting Claude Code into {} repositories", contexts.size());
		return contexts.stream().map(this::adoptOne).toList();
	}

	private AdoptionRun adoptOne(AdoptionContext context) {
		AdoptionReport report = new AdoptionReport();
		try {
			adoption.adopt(context, report);
		} catch (RuntimeException e) {
			recordFailure(context, report, e);
		}
		return new AdoptionRun(context, report);
	}

	/**
	 * The failure is logged with its stack trace here because it is not rethrown:
	 * swallowing it to keep the batch going would otherwise be the last that is seen
	 * of it. A failure the pipeline already described — naming the step that raised
	 * it — is left alone, since that reads better than the bare message.
	 */
	private void recordFailure(AdoptionContext context, AdoptionReport report, RuntimeException failure) {
		log.error("Adoption failed for {}", context.repositoryUrl(), failure);
		if (report.failure().isEmpty()) {
			report.recordFailure(Failures.describe(failure));
		}
	}
}
