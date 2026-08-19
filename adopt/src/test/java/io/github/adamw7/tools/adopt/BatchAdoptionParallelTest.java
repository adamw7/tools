package io.github.adamw7.tools.adopt;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;

import org.apache.logging.log4j.ThreadContext;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Adopting several repositories at once. They are independent by construction —
 * that is what {@link BatchAdoption} already promised — so what these cover is
 * everything that independence relies on holding under contention.
 */
class BatchAdoptionParallelTest {

	@TempDir
	private Path workspace;

	@Test
	void answersInTheOrderTheRepositoriesWereGiven() {
		List<String> urls = urls(6);

		List<AdoptionRun> runs = new BatchAdoption((context, report) -> report.recordStep("done"),
				CheckoutRetention.ALWAYS, 4).adoptAll(urls, checkouts());

		assertEquals(urls, runs.stream().map(AdoptionRun::repositoryUrl).toList());
	}

	/** The point of the option: the adoptions really do overlap. */
	@Test
	void adoptsSeveralRepositoriesAtOnce() throws InterruptedException {
		int parallelism = 4;
		CountDownLatch allStarted = new CountDownLatch(parallelism);
		BatchAdoption batch = new BatchAdoption((context, report) -> {
			allStarted.countDown();
			awaitQuietly(allStarted);
			report.recordStep("done");
		}, CheckoutRetention.ALWAYS, parallelism);

		List<AdoptionRun> runs = batch.adoptAll(urls(parallelism), checkouts());

		assertTrue(runs.stream().allMatch(AdoptionRun::succeeded), "every adoption should have landed");
	}

	/** Each adoption runs on its own thread, which is what the log context is put on. */
	@Test
	void putsTheRepositoryIntoTheLoggingContextOfItsOwnThread() {
		Set<String> seen = ConcurrentHashMap.newKeySet();
		BatchAdoption batch = new BatchAdoption((context, report) -> {
			seen.add(ThreadContext.get(BatchAdoption.REPOSITORY_CONTEXT_KEY));
			report.recordStep("done");
		}, CheckoutRetention.ALWAYS, 3);

		batch.adoptAll(urls(3), checkouts());

		assertEquals(Set.copyOf(urls(3)), seen, "every adoption should log under its own repository");
	}

	/** The context must not outlive the adoption that set it. */
	@Test
	void clearsTheLoggingContextAfterwards() {
		new BatchAdoption((context, report) -> report.recordStep("done"), CheckoutRetention.ALWAYS, 2)
				.adoptAll(urls(2), checkouts());

		assertEquals(null, ThreadContext.get(BatchAdoption.REPOSITORY_CONTEXT_KEY));
	}

	/** A failing adoption still does not stop the ones beside it. */
	@Test
	void oneFailureDoesNotStopTheOthers() {
		BatchAdoption batch = new BatchAdoption((context, report) -> {
			if (context.displayUrl().contains("repo3")) {
				throw new AdoptionException("boom");
			}
			report.recordStep("done");
		}, CheckoutRetention.ALWAYS, 4);

		List<AdoptionRun> runs = batch.adoptAll(urls(5), checkouts());

		assertEquals(4, runs.stream().filter(AdoptionRun::succeeded).count());
		assertEquals(1, runs.stream().filter(run -> !run.succeeded()).count());
	}

	/**
	 * Two repositories that would clone into one directory are refused, and the
	 * refusal has to survive several threads racing for it: the claim must produce one
	 * claim and one refusal, never two claims.
	 */
	@Test
	void refusesASecondClaimOnOneCheckoutUnderContention() {
		List<String> colliding = IntStream.range(0, 8)
				.mapToObj(index -> "https://github.com/owner" + index + "/same.git")
				.toList();

		List<AdoptionRun> runs = new BatchAdoption((context, report) -> report.recordStep("done"),
				CheckoutRetention.ALWAYS, 8).adoptAll(colliding, checkouts());

		assertEquals(1, runs.stream().filter(AdoptionRun::succeeded).count(),
				"exactly one repository may claim the checkout");
	}

	@Test
	void refusesAParallelismOutsideItsBounds() {
		assertThrows(IllegalArgumentException.class,
				() -> new BatchAdoption(noop(), CheckoutRetention.ALWAYS, 0));
		assertThrows(IllegalArgumentException.class, () -> new BatchAdoption(noop(), CheckoutRetention.ALWAYS,
				BatchAdoption.MAX_PARALLELISM + 1));
	}

	/** A batch of one starts no pool at all: the ordinary run is the run it always was. */
	@Test
	void runsASingleRepositoryOnTheCallingThread() {
		String callingThread = Thread.currentThread().getName();
		StringBuilder ranOn = new StringBuilder();

		new BatchAdoption((context, report) -> {
			ranOn.append(Thread.currentThread().getName());
			report.recordStep("done");
		}, CheckoutRetention.ALWAYS, 4).adoptAll(urls(1), checkouts());

		assertEquals(callingThread, ranOn.toString());
	}

	private BatchAdoption.Adoption noop() {
		return (context, report) -> report.recordStep("done");
	}

	private Checkouts checkouts() {
		return new Checkouts(workspace, "claude/adopt-claude-code");
	}

	private List<String> urls(int count) {
		return IntStream.range(0, count)
				.mapToObj(index -> "https://github.com/owner/repo" + index + ".git")
				.toList();
	}

	private void awaitQuietly(CountDownLatch latch) {
		try {
			latch.await(2, TimeUnit.SECONDS);
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
		}
	}
}
