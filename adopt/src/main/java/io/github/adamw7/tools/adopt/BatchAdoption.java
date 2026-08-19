package io.github.adamw7.tools.adopt;

import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.stream.IntStream;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.ThreadContext;

/**
 * Adopts a list of repositories one after another, with a fresh
 * {@link AdoptionReport} for each.
 *
 * <p>A repository whose adoption fails does not stop the batch. The adoptions are
 * independent — an expired {@code gh} login or a missing build tool says nothing
 * about the next repository — so the failure is recorded in that repository's
 * report and the run moves on, leaving the caller to decide what a partly failed
 * batch means.
 *
 * <p>That independence is also what lets a batch run several repositories at once.
 * It used to be sequential for one reason: every adoption shells out to
 * {@code git}, {@code claude} and {@code gh}, and a parallel batch interleaved
 * their output into a log nobody could attribute. That is a solvable problem
 * rather than a reason, and it is solved by putting the repository into the
 * logging context of the thread adopting it, so every line a run emits says which
 * repository it belongs to. A batch of one, or a {@code parallelism} of one, still
 * runs on the calling thread and starts no pool at all.
 *
 * <p>Each repository's checkout is settled once its adoption is over, by the
 * {@link CheckoutRetention} the run was given: a batch makes one full clone per
 * repository, and nothing used to remove them.
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

	/**
	 * The key each adoption's log lines are attributed by. Named here because the
	 * appender patterns in {@code log4j2.properties} read it, and a value put under
	 * one key and read under another attributes nothing.
	 */
	public static final String REPOSITORY_CONTEXT_KEY = "repository";

	/** One repository at a time, on the calling thread. */
	public static final int SEQUENTIAL = 1;

	/**
	 * The most an operator may ask for. Each thread runs a clone, a {@code claude
	 * init} and a build, so the limit that matters is the host's and the remote's
	 * patience rather than its cores; past this a batch is mostly waiting on GitHub,
	 * which answers by rate limiting the run.
	 */
	public static final int MAX_PARALLELISM = 8;

	private final Adoption adoption;
	private final CheckoutRetention retention;
	private final int parallelism;

	public BatchAdoption(Adoption adoption) {
		this(adoption, CheckoutRetention.ALWAYS);
	}

	/**
	 * @param retention what becomes of each repository's checkout once its adoption
	 *                  is over. A batch makes one full clone per repository and
	 *                  nothing used to remove them.
	 */
	public BatchAdoption(Adoption adoption, CheckoutRetention retention) {
		this(adoption, retention, SEQUENTIAL);
	}

	/**
	 * @param parallelism how many repositories are adopted at once, bounded by
	 *                    {@link #MAX_PARALLELISM}; {@link #SEQUENTIAL} runs them one
	 *                    at a time on the calling thread
	 */
	public BatchAdoption(Adoption adoption, CheckoutRetention retention, int parallelism) {
		this.adoption = adoption;
		this.retention = retention;
		this.parallelism = requireWithinBounds(parallelism);
	}

	private static int requireWithinBounds(int parallelism) {
		if (parallelism < SEQUENTIAL || parallelism > MAX_PARALLELISM) {
			throw new IllegalArgumentException("parallelism must be between " + SEQUENTIAL + " and "
					+ MAX_PARALLELISM + " but was " + parallelism);
		}
		return parallelism;
	}

	/**
	 * @param repositoryUrls the repositories to adopt, in the order they were given
	 * @param checkouts      this run's workspace and branch, giving each repository
	 *                       its own checkout directory
	 * @return one run per repository, in the order the repositories were given
	 */
	public List<AdoptionRun> adoptAll(List<String> repositoryUrls, Checkouts checkouts) {
		log.info("Adopting Claude Code into {} repositories on branch {}{}", repositoryUrls.size(),
				checkouts.branchName(), parallelism > SEQUENTIAL ? ", " + parallelism + " at a time" : "");
		Elapsed elapsed = Elapsed.started();
		List<AdoptionRun> runs = run(repositoryUrls, checkouts);
		logSummary(runs, elapsed);
		return runs;
	}

	/**
	 * A batch small enough to need no pool does not start one, so the ordinary
	 * single-repository run is exactly the run it always was — same thread, same
	 * stack, nothing to shut down.
	 */
	private List<AdoptionRun> run(List<String> repositoryUrls, Checkouts checkouts) {
		if (parallelism == SEQUENTIAL || repositoryUrls.size() == 1) {
			return IntStream.range(0, repositoryUrls.size())
					.mapToObj(index -> adoptOne(repositoryUrls, index, checkouts))
					.toList();
		}
		return inParallel(repositoryUrls, checkouts);
	}

	/**
	 * Adopts several repositories at once, answering in the order they were given
	 * rather than the order they finished: a run is read repository by repository,
	 * against the list the operator handed in.
	 *
	 * <p>Nothing here has to handle a failing adoption, because
	 * {@link #adoptOne} already records one and returns; a task that threw anyway
	 * would be a defect in this class rather than in a repository, so it is allowed
	 * to end the batch.
	 */
	private List<AdoptionRun> inParallel(List<String> repositoryUrls, Checkouts checkouts) {
		try (ExecutorService executor = Executors.newFixedThreadPool(parallelism)) {
			List<Future<AdoptionRun>> futures = IntStream.range(0, repositoryUrls.size())
					.mapToObj(index -> executor.submit(() -> adoptOne(repositoryUrls, index, checkouts)))
					.toList();
			return futures.stream().map(BatchAdoption::completed).toList();
		}
	}

	private static AdoptionRun completed(Future<AdoptionRun> future) {
		try {
			return future.get();
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			throw new AdoptionException("Interrupted while adopting a batch of repositories", e);
		} catch (ExecutionException e) {
			throw new AdoptionException("A repository's adoption ended unexpectedly", e.getCause());
		}
	}

	/**
	 * The URL is redacted up front so a repository that fails its claim — and so
	 * never has a context to ask — is still reported without its credentials. The
	 * repository is taken by position so the line announcing it can say which of how
	 * many it is; every line below it names a step, not a repository.
	 */
	private AdoptionRun adoptOne(List<String> repositoryUrls, int index, Checkouts checkouts) {
		String repositoryUrl = repositoryUrls.get(index);
		AdoptionReport report = new AdoptionReport();
		String displayUrl = Redaction.of(repositoryUrl);
		ThreadContext.put(REPOSITORY_CONTEXT_KEY, displayUrl);
		try {
			log.info("Repository {} of {}: {}", index + 1, repositoryUrls.size(), displayUrl);
			adoptClaimed(repositoryUrl, displayUrl, checkouts, report);
		} finally {
			ThreadContext.remove(REPOSITORY_CONTEXT_KEY);
		}
		return new AdoptionRun(displayUrl, checkouts.branchName(), report);
	}

	private void adoptClaimed(String repositoryUrl, String displayUrl, Checkouts checkouts,
			AdoptionReport report) {
		try {
			adopt(checkouts.claim(repositoryUrl), report);
		} catch (RuntimeException e) {
			recordFailure(displayUrl, report, e);
		}
	}

	/**
	 * Adopts one repository and then settles its checkout, on the failure path as
	 * well as the successful one — a claim that threw has no context and so no
	 * checkout to settle, which is why the claim sits outside this.
	 */
	private void adopt(AdoptionContext context, AdoptionReport report) {
		try {
			adoption.adopt(context, report);
		} finally {
			retention.apply(context.repositoryDirectory(), report.succeeded());
		}
	}

	/**
	 * Closes the batch with what the operator has to act on: how much of it landed,
	 * what it cost, and — when some of it did not — which repositories to re-run.
	 * Those failures are scattered through however many repositories' worth of steps
	 * separate them, so naming them together at the end is the difference between
	 * reading the run and searching it.
	 */
	private void logSummary(List<AdoptionRun> runs, Elapsed elapsed) {
		List<String> failed = runs.stream().filter(run -> !run.succeeded()).map(AdoptionRun::repositoryUrl).toList();
		log.info("Adopted {} of {} repositories in {}", runs.size() - failed.size(), runs.size(), elapsed);
		if (!failed.isEmpty()) {
			log.warn("Adoption failed for {} of {} repositories: {}", failed.size(), runs.size(), failed);
		}
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
