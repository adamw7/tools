package io.github.adamw7.tools.adopt.command;

import java.time.Duration;

/**
 * The wait before a retry: {@code first} before the second attempt, doubling before
 * each one after it, and never more than {@code max}. Both retries in an adoption
 * &mdash; the transport retry in {@link RetryingCommandRunner} and the retried
 * {@code claude init} &mdash; follow this one policy with bounds of their own.
 *
 * @param first the wait before the second attempt
 * @param max   the wait the doubling stops at, so a generous retry count cannot idle a
 *              run for minutes
 */
public record Backoff(Duration first, Duration max) {

	/**
	 * Past this many doublings the wait is already far above any sane {@code max}, and
	 * shifting further would overflow the multiplier into a negative wait.
	 */
	private static final int MAX_DOUBLINGS = 30;

	/**
	 * The wait before retry number {@code attempt}, counting the first retry as 1.
	 */
	public Duration before(int attempt) {
		Duration doubled = first.multipliedBy(1L << Math.min(attempt - 1, MAX_DOUBLINGS));
		return doubled.compareTo(max) > 0 ? max : doubled;
	}
}
