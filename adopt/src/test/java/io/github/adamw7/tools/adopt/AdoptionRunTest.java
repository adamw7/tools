package io.github.adamw7.tools.adopt;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.Optional;

import org.junit.jupiter.api.Test;

class AdoptionRunTest {

	private static final String REPO_URL = "https://github.com/owner/repo.git";

	private final AdoptionContext context = new AdoptionContext(REPO_URL, Path.of("/tmp/workspace"));
	private final AdoptionReport report = new AdoptionReport();

	/**
	 * A report on its own does not say which repository it belongs to, which is the
	 * whole reason a batch answers with runs rather than bare reports.
	 */
	@Test
	void namesTheRepositoryItsReportBelongsTo() {
		assertEquals(REPO_URL, new AdoptionRun(context, report).repositoryUrl());
	}

	@Test
	void keepsTheContextAndReportItWasGiven() {
		AdoptionRun run = new AdoptionRun(context, report);
		assertEquals(context, run.context());
		assertEquals(report, run.report());
	}

	@Test
	void succeedsWhileItsReportRecordsNoFailure() {
		AdoptionRun run = new AdoptionRun(context, report);
		assertTrue(run.succeeded());
		assertEquals(Optional.empty(), run.failure());
	}

	@Test
	void failsWithTheReasonItsReportRecorded() {
		report.recordFailure("clone: repository not found");
		AdoptionRun run = new AdoptionRun(context, report);
		assertFalse(run.succeeded());
		assertEquals("clone: repository not found", run.failure().orElseThrow());
	}
}
