package io.github.adamw7.tools.adopt.command;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;

/**
 * Lifting the wait out of what GitHub said. A rate limit is not retried — that is
 * what the limit exists to stop — so the operator is told instead, and being told
 * means being told when.
 */
class RateLimitsTest {

	@Test
	void recognisesTheWordingGitHubUsesForALimit() {
		for (String transcript : List.of(
				"You have exceeded a secondary rate limit",
				"API rate limit exceeded for user ID 1",
				"You have triggered an abuse detection mechanism",
				"HTTP 429: Too Many Requests")) {
			assertTrue(RateLimits.reported(transcript), transcript);
		}
	}

	@Test
	void doesNotReadAnOrdinaryRefusalAsALimit() {
		for (String transcript : List.of(
				"fatal: Authentication failed",
				"The requested URL returned error: 403",
				"remote: Permission to owner/repo.git denied")) {
			assertFalse(RateLimits.reported(transcript), transcript);
		}
	}

	@Test
	void readsTheWaitInSecondsMinutesAndHours() {
		assertEquals(Optional.of(Duration.ofSeconds(30)), RateLimits.waitFrom("Retry-After: 30 seconds"));
		assertEquals(Optional.of(Duration.ofMinutes(6)), RateLimits.waitFrom("please try again in 6 minutes"));
		assertEquals(Optional.of(Duration.ofHours(1)), RateLimits.waitFrom("wait 1 hour before trying again"));
	}

	@Test
	void readsNoWaitFromATranscriptThatNamesNone() {
		assertEquals(Optional.empty(), RateLimits.waitFrom("You have exceeded a secondary rate limit"));
	}

	/** A number nobody wrote is worse than no number, so the notice says only what it knows. */
	@Test
	void describesALimitThatNamedNoWaitWithoutInventingOne() {
		String notice = RateLimits.describe("You have exceeded a secondary rate limit").orElseThrow();

		assertTrue(notice.contains("rate limiting"), notice);
		assertFalse(notice.contains("about"), notice);
	}

	@Test
	void describesTheWaitWhereGitHubNamedOne() {
		String notice = RateLimits.describe(
				"You have exceeded a secondary rate limit. Please wait 6 minutes before trying again.")
				.orElseThrow();

		assertTrue(notice.contains("6 minutes"), notice);
	}

	/** A wait is acted on rather than measured, so a part-minute rounds up. */
	@Test
	void roundsAPartMinuteUp() {
		assertTrue(RateLimits.describe("rate limit; retry-after 90 seconds").orElseThrow().contains("2 minutes"));
	}

	@Test
	void describesNothingForATranscriptWithNoLimitInIt() {
		assertEquals(Optional.empty(), RateLimits.describe("fatal: Authentication failed"));
	}
}
