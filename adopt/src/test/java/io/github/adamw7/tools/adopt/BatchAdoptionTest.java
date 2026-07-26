package io.github.adamw7.tools.adopt;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;

class BatchAdoptionTest {

	private static final String REPO_URL = "https://github.com/owner/repo.git";
	private static final String OTHER_URL = "https://github.com/owner/other.git";

	private final List<String> adopted = new ArrayList<>();

	@Test
	void adoptsEveryRepositoryInOrder() {
		List<AdoptionRun> runs = new BatchAdoption(this::record).adoptAll(contexts(REPO_URL, OTHER_URL));
		assertEquals(List.of(REPO_URL, OTHER_URL), adopted);
		assertEquals(List.of(REPO_URL, OTHER_URL), runs.stream().map(AdoptionRun::repositoryUrl).toList());
		assertTrue(runs.stream().allMatch(AdoptionRun::succeeded));
	}

	@Test
	void givesEachRepositoryItsOwnReport() {
		List<AdoptionRun> runs = new BatchAdoption((context, report) -> report.recordStep(context.repositoryUrl()))
				.adoptAll(contexts(REPO_URL, OTHER_URL));
		assertEquals(List.of(REPO_URL), runs.get(0).report().completedSteps());
		assertEquals(List.of(OTHER_URL), runs.get(1).report().completedSteps());
	}

	/**
	 * The adoptions are independent, so a repository nobody can clone must not strand
	 * the ones behind it on the list.
	 */
	@Test
	void keepsGoingAfterARepositoryFails() {
		List<AdoptionRun> runs = new BatchAdoption(this::failFirst).adoptAll(contexts(REPO_URL, OTHER_URL));
		assertEquals(List.of(REPO_URL, OTHER_URL), adopted);
		assertFalse(runs.get(0).succeeded());
		assertEquals("boom", runs.get(0).failure().orElseThrow());
		assertTrue(runs.get(1).succeeded());
	}

	@Test
	void keepsTheStepThePipelineAlreadyBlamed() {
		List<AdoptionRun> runs = new BatchAdoption((context, report) -> {
			report.recordFailure("push: rejected");
			throw new AdoptionException("boom");
		}).adoptAll(contexts(REPO_URL));
		assertEquals("push: rejected", runs.get(0).failure().orElseThrow());
	}

	/** A failure with no message would otherwise record a bare null as the reason. */
	@Test
	void fallsBackToTheFailuresTypeWhenItCarriesNoMessage() {
		List<AdoptionRun> runs = new BatchAdoption((context, report) -> {
			throw new IllegalStateException();
		}).adoptAll(contexts(REPO_URL));
		assertEquals("IllegalStateException", runs.get(0).failure().orElseThrow());
	}

	/**
	 * How far a failed repository got is the report's most useful line, so the steps
	 * it did complete must survive the failure that stopped it.
	 */
	@Test
	void keepsTheStepsARepositoryCompletedBeforeItFailed() {
		List<AdoptionRun> runs = new BatchAdoption((context, report) -> {
			report.recordStep("clone");
			throw new AdoptionException("boom");
		}).adoptAll(contexts(REPO_URL));
		assertEquals(List.of("clone"), runs.get(0).report().completedSteps());
	}

	@Test
	void answersWithTheContextEachRepositoryWasAdoptedWith() {
		List<AdoptionContext> contexts = contexts(REPO_URL, OTHER_URL);
		List<AdoptionRun> runs = new BatchAdoption(this::record).adoptAll(contexts);
		assertEquals(contexts, runs.stream().map(AdoptionRun::context).toList());
	}

	/**
	 * A failure raised outside the pipeline — an argument the steps never saw, an
	 * adoption that threw before recording anything — must still be reported as this
	 * repository's, not swallowed with the batch carrying on silently.
	 */
	@Test
	void recordsAFailureThePipelineNeverDescribed() {
		List<AdoptionRun> runs = new BatchAdoption((context, report) -> {
			throw new IllegalArgumentException("workspace must not be null");
		}).adoptAll(contexts(REPO_URL));
		assertFalse(runs.get(0).succeeded());
		assertEquals("workspace must not be null", runs.get(0).failure().orElseThrow());
	}

	@Test
	void adoptsNothingWhenGivenNoRepositories() {
		assertTrue(new BatchAdoption(this::record).adoptAll(List.of()).isEmpty());
	}

	private void record(AdoptionContext context, AdoptionReport report) {
		adopted.add(context.repositoryUrl());
	}

	private void failFirst(AdoptionContext context, AdoptionReport report) {
		record(context, report);
		if (REPO_URL.equals(context.repositoryUrl())) {
			throw new AdoptionException("boom");
		}
	}

	private List<AdoptionContext> contexts(String... urls) {
		return Stream.of(urls).map(url -> new AdoptionContext(url, Path.of("/tmp/workspace"))).toList();
	}
}
