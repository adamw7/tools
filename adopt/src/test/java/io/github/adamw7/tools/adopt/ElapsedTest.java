package io.github.adamw7.tools.adopt;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;

import org.junit.jupiter.api.Test;

class ElapsedTest {

	@Test
	void readsSubSecondDurationsInMilliseconds() {
		assertEquals("0ms", Elapsed.describe(Duration.ZERO));
		assertEquals("317ms", Elapsed.describe(Duration.ofMillis(317)));
		assertEquals("999ms", Elapsed.describe(Duration.ofMillis(999)));
	}

	/** Rounding the short commands to whole seconds would report every one of them as 0s. */
	@Test
	void switchesToSecondsAtOneSecond() {
		assertEquals("1s", Elapsed.describe(Duration.ofMillis(1000)));
		assertEquals("1s", Elapsed.describe(Duration.ofMillis(1999)));
		assertEquals("59s", Elapsed.describe(Duration.ofSeconds(59)));
	}

	@Test
	void switchesToMinutesAndSecondsAtOneMinute() {
		assertEquals("1m 0s", Elapsed.describe(Duration.ofSeconds(60)));
		assertEquals("4m 12s", Elapsed.describe(Duration.ofSeconds(252)));
		assertEquals("70m 1s", Elapsed.describe(Duration.ofSeconds(4201)));
	}

	/** Sub-second precision is dropped once a duration is worth minutes, not lost from the minutes. */
	@Test
	void ignoresTheFractionOfASecondBeyondAMinute() {
		assertEquals("2m 30s", Elapsed.describe(Duration.ofSeconds(150).plusMillis(750)));
	}

	@Test
	void measuresFromWhenItWasStarted() {
		Elapsed elapsed = Elapsed.started();

		assertFalse(elapsed.duration().isNegative(), "a stopwatch cannot run backwards");
		assertTrue(elapsed.duration().compareTo(Duration.ofMinutes(1)) < 0,
				"a stopwatch just started cannot already read a minute");
	}

	/** The log lines interpolate the stopwatch itself, so its text is what toString answers. */
	@Test
	void readsAsItsOwnDescription() {
		Elapsed elapsed = Elapsed.started();

		assertEquals(Elapsed.describe(elapsed.duration()).replaceAll("\\d+", "n"),
				elapsed.toString().replaceAll("\\d+", "n"));
	}
}
