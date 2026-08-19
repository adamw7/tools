package io.github.adamw7.tools.adopt;

import java.time.Duration;

/**
 * A stopwatch for the log: started when the work starts, and read as text when the
 * line saying how it went is written. An adoption is a sequence of commands that
 * each take between a millisecond and ten minutes, so how long a step took is the
 * question an operator asks first of a run that felt slow.
 *
 * <p>Measured with {@link System#nanoTime()} rather than the wall clock, so a clock
 * correction landing mid-command does not rewrite a duration. {@link #toString()} is
 * what a log line interpolates, in place of the ISO-8601
 * {@link Duration#toString()} a reader would have to decode.
 */
public final class Elapsed {

	private static final long MILLIS_PER_SECOND = 1000;
	private static final long SECONDS_PER_MINUTE = 60;

	private final long startedAtNanos;

	private Elapsed(long startedAtNanos) {
		this.startedAtNanos = startedAtNanos;
	}

	/** @return a stopwatch started now */
	public static Elapsed started() {
		return new Elapsed(System.nanoTime());
	}

	/** @return how long it has been since this stopwatch was started */
	public Duration duration() {
		return Duration.ofNanos(System.nanoTime() - startedAtNanos);
	}

	/** @return the elapsed time as a log line reads it, e.g. {@code 317ms}, {@code 42s} or {@code 4m 12s} */
	@Override
	public String toString() {
		return describe(duration());
	}

	/**
	 * Each magnitude is given the unit that carries the information at that scale.
	 * A {@code git rev-parse} is worth a millisecond count, a {@code claude init}
	 * is not — {@code 252317ms} says nothing {@code 4m 12s} does not say better —
	 * and rounding the short commands to whole seconds would report every one of
	 * them as {@code 0s}.
	 */
	static String describe(Duration duration) {
		long millis = duration.toMillis();
		if (millis < MILLIS_PER_SECOND) {
			return millis + "ms";
		}
		long seconds = duration.toSeconds();
		if (seconds < SECONDS_PER_MINUTE) {
			return seconds + "s";
		}
		return duration.toMinutes() + "m " + seconds % SECONDS_PER_MINUTE + "s";
	}
}
