package io.github.adamw7.tools.adopt.command;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.Duration;

import org.junit.jupiter.api.Test;

class BackoffTest {

	private final Backoff backoff = new Backoff(Duration.ofSeconds(2), Duration.ofSeconds(30));

	@Test
	void doublesFromTheFirstWait() {
		assertEquals(Duration.ofSeconds(2), backoff.before(1));
		assertEquals(Duration.ofSeconds(4), backoff.before(2));
		assertEquals(Duration.ofSeconds(16), backoff.before(4));
	}

	@Test
	void stopsAtTheCap() {
		assertEquals(Duration.ofSeconds(30), backoff.before(5));
		assertEquals(Duration.ofSeconds(30), backoff.before(10));
	}

	/** Shifting past 63 doublings wrapped the multiplier negative, and a negative wait is below any cap. */
	@Test
	void staysAtTheCapForAnAttemptPastTheShiftWidth() {
		assertEquals(Duration.ofSeconds(30), backoff.before(64));
		assertEquals(Duration.ofSeconds(30), backoff.before(Integer.MAX_VALUE));
	}
}
