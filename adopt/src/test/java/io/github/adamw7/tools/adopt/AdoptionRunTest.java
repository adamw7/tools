package io.github.adamw7.tools.adopt;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Optional;

import org.junit.jupiter.api.Test;

class AdoptionRunTest {

	private static final String REPO_URL = "https://github.com/owner/repo.git";
	private static final String BRANCH = "claude/adopt-claude-code";

	private final AdoptionReport report = new AdoptionReport();

	/**
	 * A report on its own does not say which repository it belongs to, which is the
	 * whole reason a batch answers with runs rather than bare reports.
	 */
	@Test
	void namesTheRepositoryItsReportBelongsTo() {
		assertEquals(REPO_URL, run().repositoryUrl());
	}

	@Test
	void keepsTheBranchAndReportItWasGiven() {
		assertEquals(BRANCH, run().branchName());
		assertEquals(report, run().report());
	}

	@Test
	void succeedsWhileItsReportRecordsNoFailure() {
		assertTrue(run().succeeded());
		assertEquals(Optional.empty(), run().failure());
	}

	@Test
	void failsWithTheReasonItsReportRecorded() {
		report.recordFailure("clone: repository not found");
		assertFalse(run().succeeded());
		assertEquals("clone: repository not found", run().failure().orElseThrow());
	}

	private AdoptionRun run() {
		return new AdoptionRun(REPO_URL, BRANCH, report);
	}
}
