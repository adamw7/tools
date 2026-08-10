package io.github.adamw7.tools.adopt;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

class AdoptionReportTest {

	@Test
	void startsEmpty() {
		AdoptionReport report = new AdoptionReport();
		assertTrue(report.completedSteps().isEmpty());
		assertTrue(report.checkout().isEmpty());
		assertTrue(report.pullRequestUrl().isEmpty());
		assertTrue(report.failure().isEmpty());
		assertTrue(report.succeeded());
	}

	@Test
	void recordsCheckout() {
		AdoptionReport report = new AdoptionReport();
		report.recordCheckout("/tmp/claude-adopt-1234/repo");
		assertEquals("/tmp/claude-adopt-1234/repo", report.checkout().orElseThrow());
	}

	@Test
	void recordsFailure() {
		AdoptionReport report = new AdoptionReport();
		report.recordStep("clone");
		report.recordFailure("push: the requested URL returned error: 403");
		assertEquals("push: the requested URL returned error: 403", report.failure().orElseThrow());
		assertFalse(report.succeeded());
		assertEquals(List.of("clone"), report.completedSteps(),
				"a failed run still reports the steps that did complete");
	}

	@Test
	void recordsStepsInOrder() {
		AdoptionReport report = new AdoptionReport();
		report.recordStep("clone");
		report.recordStep("branch");
		assertEquals(List.of("clone", "branch"), report.completedSteps());
	}

	@Test
	void recordsPullRequestUrl() {
		AdoptionReport report = new AdoptionReport();
		report.recordPullRequestUrl("https://github.com/owner/repo/pull/1");
		assertEquals("https://github.com/owner/repo/pull/1", report.pullRequestUrl().orElseThrow());
	}

	@Test
	void completedStepsAreACopy() {
		AdoptionReport report = new AdoptionReport();
		report.recordStep("clone");
		List<String> steps = report.completedSteps();
		report.recordStep("branch");
		assertEquals(List.of("clone"), steps);
	}
}
