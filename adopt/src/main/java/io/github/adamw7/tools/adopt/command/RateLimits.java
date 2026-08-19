package io.github.adamw7.tools.adopt.command;

import java.time.Duration;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Reads a rate limit out of what {@code git} or {@code gh} said, so a run stopped
 * by one says when to try again.
 *
 * <p>A rate limit is deliberately not retried — {@link TransientFailures} leaves it
 * out, GitHub answering a secondary limit by asking for a wait measured in minutes.
 * The operator is told instead, but what they were told was the raw transcript, with
 * the wait somewhere among the API's prose. So it is lifted out into the failure's
 * first line; nothing here changes what is retried.
 */
public final class RateLimits {

	/**
	 * How GitHub's own wording names the wait. Minutes and seconds are both matched
	 * because the API uses both — a secondary limit asks for minutes, a primary one
	 * reports seconds — and either is an answer to the operator's question.
	 */
	private static final Pattern WAIT = Pattern.compile(
			"(?:retry[- ]after|try again in|wait)\\D{0,20}?(\\d{1,5})\\s*(second|minute|hour)",
			Pattern.CASE_INSENSITIVE);

	/**
	 * The wording that says a limit was hit at all. A transcript may name a limit
	 * without naming a wait, and that is still worth reporting as a rate limit rather
	 * than as an unexplained refusal.
	 */
	private static final Pattern LIMIT = Pattern.compile(
			"rate limit|secondary rate|abuse detection|too many requests|\\b429\\b",
			Pattern.CASE_INSENSITIVE);

	private RateLimits() {
	}

	/** @return whether the transcript reports the run being rate limited */
	public static boolean reported(String output) {
		return LIMIT.matcher(output).find();
	}

	/**
	 * @return how long the transcript asks the run to wait, or empty when it reports a
	 *         limit without naming one — in which case the operator is told that much
	 *         and no more, rather than being given a number nobody wrote
	 */
	public static Optional<Duration> waitFrom(String output) {
		Matcher matcher = WAIT.matcher(output);
		if (!matcher.find()) {
			return Optional.empty();
		}
		return Optional.of(durationOf(Long.parseLong(matcher.group(1)), matcher.group(2)));
	}

	/**
	 * The line a failed command's report opens with when a rate limit stopped it, or
	 * empty when nothing did.
	 *
	 * @param output the command's transcript
	 */
	public static Optional<String> describe(String output) {
		if (!reported(output)) {
			return Optional.empty();
		}
		return Optional.of(waitFrom(output)
				.map(wait -> "GitHub is rate limiting this run; try again in about " + humanized(wait) + ".")
				.orElse("GitHub is rate limiting this run; try again later."));
	}

	private static Duration durationOf(long amount, String unit) {
		return switch (unit.toLowerCase(Locale.ROOT)) {
			case "hour" -> Duration.ofHours(amount);
			case "minute" -> Duration.ofMinutes(amount);
			default -> Duration.ofSeconds(amount);
		};
	}

	/** Rounded up to the next minute past a minute, since a wait is acted on, not measured. */
	private static String humanized(Duration wait) {
		if (wait.toMinutes() < 1) {
			return wait.toSeconds() + "s";
		}
		long minutes = wait.plusSeconds(59).toMinutes();
		return minutes < 60 ? minutes + " minutes" : wait.toHours() + "h" + wait.toMinutesPart() + "m";
	}
}
