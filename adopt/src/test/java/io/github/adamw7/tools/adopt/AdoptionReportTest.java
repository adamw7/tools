package io.github.adamw7.tools.adopt;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

class AdoptionReportTest {

	@Test
	void startsEmpty() {
		AdoptionReport report = new AdoptionReport();
		assertTrue(report.completedSteps().isEmpty());
		assertTrue(report.pullRequestUrl().isEmpty());
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
