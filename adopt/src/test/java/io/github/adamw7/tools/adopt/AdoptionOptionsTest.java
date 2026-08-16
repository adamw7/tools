package io.github.adamw7.tools.adopt;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import io.github.adamw7.tools.adopt.command.ProcessCommandRunner;
import io.github.adamw7.tools.adopt.step.PullRequestOptions;

class AdoptionOptionsTest {

	@Test
	void defaultsAdoptForRealWithTheAdoptionsOwnPullRequest() {
		AdoptionOptions options = AdoptionOptions.defaults();
		assertEquals(PullRequestOptions.defaults(), options.pullRequest());
		assertFalse(options.includeAssets());
		assertFalse(options.dryRun());
		assertEquals(Optional.empty(), options.pinnedRuleVersion());
		assertEquals(ProcessCommandRunner.DEFAULT_TIMEOUT, options.commandTimeout());
		assertEquals(AdoptionOptions.DEFAULT_RETRIES, options.retries());
	}

	/**
	 * A retry count is refused where the options are built, so a caller assembling the
	 * pipeline for itself hears about it before the run rather than through the runner
	 * mid-adoption.
	 */
	@Test
	void refusesARetryCountOutsideTheBounds() {
		assertThrows(IllegalArgumentException.class, () -> retrying(-1));
		assertThrows(IllegalArgumentException.class, () -> retrying(AdoptionOptions.MAX_RETRIES + 1));
	}

	/** Zero is an answer, not an absence: every failure is reported the moment it happens. */
	@Test
	void keepsARetryCountTheCallerNamed() {
		assertEquals(0, retrying(0).retries());
		assertEquals(AdoptionOptions.MAX_RETRIES, retrying(AdoptionOptions.MAX_RETRIES).retries());
	}

	/**
	 * Both entry points hand over what their caller supplied, absences included, so
	 * the record fills them in rather than leaving each one to remember to.
	 */
	@Test
	void fillsInWhatWasNotSupplied() {
		AdoptionOptions options = new AdoptionOptions(null, false, null, false, null, AdoptionOptions.DEFAULT_RETRIES);
		assertEquals(PullRequestOptions.defaults(), options.pullRequest());
		assertEquals(ProcessCommandRunner.DEFAULT_TIMEOUT, options.commandTimeout());
	}

	/**
	 * A blank rule version is "not supplied" — the rule {@link Text} defines for every
	 * optional input — so the running build's version is resolved rather than a blank
	 * one being wired into the adopted POM.
	 */
	@Test
	void aBlankRuleVersionIsNotSupplied() {
		assertEquals(Optional.empty(), optionsPinning("   ").pinnedRuleVersion());
		assertEquals(Optional.empty(), optionsPinning(null).pinnedRuleVersion());
	}

	@Test
	void aNamedRuleVersionIsStrippedAndKept() {
		assertEquals(Optional.of("2.6.0"), optionsPinning(" 2.6.0 ").pinnedRuleVersion());
	}

	/**
	 * A zero or negative timeout would kill every command before it started, so it is
	 * refused where the options are built rather than at the first command that ran.
	 */
	@Test
	void refusesATimeoutThatIsNotPositive() {
		assertThrows(IllegalArgumentException.class, () -> timingOut(Duration.ZERO));
		assertThrows(IllegalArgumentException.class, () -> timingOut(Duration.ofMinutes(-1)));
	}

	@Test
	void keepsATimeoutTheCallerNamed() {
		assertEquals(Duration.ofMinutes(30), timingOut(Duration.ofMinutes(30)).commandTimeout());
		assertEquals(AdoptionOptions.MAX_TIMEOUT, timingOut(AdoptionOptions.MAX_TIMEOUT).commandTimeout());
	}

	/**
	 * The bound lives here rather than at each entry point, so a caller assembling the
	 * pipeline for itself is held to it too — a command budget outlasting the run that
	 * set it is a command nothing reclaims, whether the run is a long-lived MCP server
	 * or an unattended batch.
	 */
	@Test
	void refusesATimeoutBeyondTheBound() {
		assertThrows(IllegalArgumentException.class,
				() -> timingOut(AdoptionOptions.MAX_TIMEOUT.plusMinutes(1)));
	}

	/** The pull request is copied defensively by its own record, so the options carry that too. */
	@Test
	void carriesThePullRequestMetadataItWasGiven() {
		PullRequestOptions pullRequest = new PullRequestOptions("Title", "Body", List.of("octocat"), List.of(),
				List.of(), true);
		AdoptionOptions options = new AdoptionOptions(pullRequest, true, "2.6.0", true, Duration.ofMinutes(1), 1);
		assertEquals(pullRequest, options.pullRequest());
		assertTrue(options.includeAssets());
		assertTrue(options.dryRun());
	}

	private AdoptionOptions optionsPinning(String ruleVersion) {
		return new AdoptionOptions(PullRequestOptions.defaults(), false, ruleVersion, false, null,
				AdoptionOptions.DEFAULT_RETRIES);
	}

	private AdoptionOptions retrying(int retries) {
		return new AdoptionOptions(PullRequestOptions.defaults(), false, null, false, null, retries);
	}

	private AdoptionOptions timingOut(Duration commandTimeout) {
		return new AdoptionOptions(PullRequestOptions.defaults(), false, null, false, commandTimeout,
				AdoptionOptions.DEFAULT_RETRIES);
	}
}
