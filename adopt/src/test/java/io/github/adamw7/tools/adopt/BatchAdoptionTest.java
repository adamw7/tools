package io.github.adamw7.tools.adopt;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

class BatchAdoptionTest {

	private static final String REPO_URL = "https://github.com/owner/repo.git";
	private static final String OTHER_URL = "https://github.com/owner/other.git";
	private static final String BRANCH = "claude/adopt-claude-code";
	private static final Path WORKSPACE = Path.of("/tmp/workspace");

	private final List<String> adopted = new ArrayList<>();

	@Test
	void adoptsEveryRepositoryInOrder() {
		List<AdoptionRun> runs = adoptAll(this::record, REPO_URL, OTHER_URL);
		assertEquals(List.of(REPO_URL, OTHER_URL), adopted);
		assertEquals(List.of(REPO_URL, OTHER_URL), runs.stream().map(AdoptionRun::repositoryUrl).toList());
		assertTrue(runs.stream().allMatch(AdoptionRun::succeeded));
	}

	@Test
	void givesEachRepositoryItsOwnReport() {
		List<AdoptionRun> runs = adoptAll((context, report) -> report.recordStep(context.repositoryUrl()),
				REPO_URL, OTHER_URL);
		assertEquals(List.of(REPO_URL), runs.get(0).report().completedSteps());
		assertEquals(List.of(OTHER_URL), runs.get(1).report().completedSteps());
	}

	/**
	 * The adoptions are independent, so a repository nobody can clone must not strand
	 * the ones behind it on the list.
	 */
	@Test
	void keepsGoingAfterARepositoryFails() {
		List<AdoptionRun> runs = adoptAll(this::failFirst, REPO_URL, OTHER_URL);
		assertEquals(List.of(REPO_URL, OTHER_URL), adopted);
		assertFalse(runs.get(0).succeeded());
		assertEquals("boom", runs.get(0).failure().orElseThrow());
		assertTrue(runs.get(1).succeeded());
	}

	@Test
	void keepsTheStepThePipelineAlreadyBlamed() {
		List<AdoptionRun> runs = adoptAll((context, report) -> {
			report.recordFailure("push: rejected");
			throw new AdoptionException("boom");
		}, REPO_URL);
		assertEquals("push: rejected", runs.get(0).failure().orElseThrow());
	}

	/** A failure with no message would otherwise record a bare null as the reason. */
	@Test
	void fallsBackToTheFailuresTypeWhenItCarriesNoMessage() {
		List<AdoptionRun> runs = adoptAll((context, report) -> {
			throw new IllegalStateException();
		}, REPO_URL);
		assertEquals("IllegalStateException", runs.get(0).failure().orElseThrow());
	}

	/**
	 * How far a failed repository got is the report's most useful line, so the steps
	 * it did complete must survive the failure that stopped it.
	 */
	@Test
	void keepsTheStepsARepositoryCompletedBeforeItFailed() {
		List<AdoptionRun> runs = adoptAll((context, report) -> {
			report.recordStep("clone");
			throw new AdoptionException("boom");
		}, REPO_URL);
		assertEquals(List.of("clone"), runs.get(0).report().completedSteps());
	}

	@Test
	void answersWithTheBranchEachRepositoryWasAdoptedOn() {
		List<AdoptionRun> runs = adoptAll(this::record, REPO_URL, OTHER_URL);
		assertEquals(List.of(BRANCH, BRANCH), runs.stream().map(AdoptionRun::branchName).toList());
	}

	/**
	 * A failure raised outside the pipeline — an argument the steps never saw, an
	 * adoption that threw before recording anything — must still be reported as this
	 * repository's, not swallowed with the batch carrying on silently.
	 */
	@Test
	void recordsAFailureThePipelineNeverDescribed() {
		List<AdoptionRun> runs = adoptAll((context, report) -> {
			throw new IllegalArgumentException("workspace must not be null");
		}, REPO_URL);
		assertFalse(runs.get(0).succeeded());
		assertEquals("workspace must not be null", runs.get(0).failure().orElseThrow());
	}

	/**
	 * A URL that names no repository never reaches the pipeline, so before the claim
	 * moved into the batch it aborted the whole run before its first clone — and left
	 * no report to say which repositories had been asked for.
	 */
	@Test
	void reportsAUrlThatNamesNoRepositoryWithoutStrandingTheRest() {
		List<AdoptionRun> runs = adoptAll(this::record, "https://github.com/owner/.git", OTHER_URL);
		assertEquals(2, runs.size());
		assertFalse(runs.get(0).succeeded());
		assertTrue(runs.get(0).failure().orElseThrow().contains("must end in a repository name"),
				runs.get(0).failure().orElseThrow());
		assertTrue(runs.get(1).succeeded());
		assertEquals(List.of(OTHER_URL), adopted);
	}

	/** The repository that could not be placed is still named in its own run. */
	@Test
	void namesTheRepositoryWhoseClaimWasRefused() {
		List<AdoptionRun> runs = adoptAll(this::record, "https://github.com/owner/.git");
		assertEquals("https://github.com/owner/.git", runs.get(0).repositoryUrl());
	}

	/**
	 * Two repositories that would clone into one checkout are the second one's
	 * failure, so the first is adopted rather than both being refused.
	 */
	@Test
	void reportsACollidingCheckoutAsTheSecondRepositorysFailure() {
		List<AdoptionRun> runs = adoptAll(this::record, "https://github.com/owner/tools.git",
				"https://github.com/other-owner/tools.git");
		assertTrue(runs.get(0).succeeded());
		assertFalse(runs.get(1).succeeded());
		assertTrue(runs.get(1).failure().orElseThrow().contains("both would clone into"),
				runs.get(1).failure().orElseThrow());
	}

	/** A run is written to the report file, so a credentialled URL must not reach it. */
	@Test
	void masksTheCredentialsOfAReportedUrl() {
		List<AdoptionRun> runs = adoptAll(this::record, "https://x-access-token:secret@github.com/owner/repo.git");
		assertEquals("https://***@github.com/owner/repo.git", runs.get(0).repositoryUrl());
	}

	@Test
	void adoptsNothingWhenGivenNoRepositories() {
		assertTrue(adoptAll(this::record).isEmpty());
	}

	private List<AdoptionRun> adoptAll(BatchAdoption.Adoption adoption, String... repositoryUrls) {
		return new BatchAdoption(adoption).adoptAll(List.of(repositoryUrls), new Checkouts(WORKSPACE, BRANCH));
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
}
