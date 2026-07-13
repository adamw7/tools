package io.github.adamw7.tools.enforcer.doc;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.maven.enforcer.rule.api.EnforcerRuleException;

import io.github.adamw7.tools.enforcer.doc.BoundedCharSequence.BacktrackLimitExceededException;

/**
 * Compares two named documents against a list of single-group regular
 * expressions and reports where a captured value differs. Shared by
 * {@link CrossDocConsistencyRule} and {@link ReadmeConsistencyRule} so the
 * pattern validation and capture logic live in one place.
 * <p>
 * Each configured pattern must declare one capturing group; the value captured
 * from each document is compared and a mismatch becomes a violation message.
 * The two rules differ only in how they treat a fact that appears in one
 * document but not the other: mirror documents (CLAUDE.md and AGENTS.md) require
 * it in both, while a curated view (README.md against the agent docs) ignores a
 * fact the view simply chose not to repeat. That choice is the
 * {@code requireInBoth} flag passed to {@link #violations}.
 */
final class DocumentConsistency {

	/**
	 * Per-character budget granted to a single {@code find} relative to the
	 * document length, plus a flat floor so short documents still get room. A
	 * linear pattern reads each character a small constant number of times and
	 * stays well within this; only exponential backtracking blows past it.
	 */
	private static final long STEPS_PER_CHARACTER = 10_000L;
	private static final long MINIMUM_STEPS = 1_000_000L;

	/** A document to compare: its content, and the name shown in violation messages. */
	record Document(String name, String content) {
	}

	private final List<String> patterns;

	DocumentConsistency(List<String> patterns) {
		this.patterns = patterns != null ? patterns : List.of();
	}

	/**
	 * Each pattern must declare a capturing group, since comparison reads
	 * {@code group(1)}. A pattern without one is a build-setup mistake, so it fails
	 * with a clear message instead of letting an opaque
	 * {@link IndexOutOfBoundsException} escape at match time.
	 */
	void verifyPatterns() throws EnforcerRuleException {
		for (String pattern : patterns) {
			if (Pattern.compile(pattern).matcher("").groupCount() < 1) {
				throw new EnforcerRuleException(
						"consistentPattern '" + pattern + "' must declare a capturing group");
			}
		}
	}

	/**
	 * Collects one violation per pattern whose captured values disagree. When
	 * {@code requireInBoth} is true a fact present in only one document is a
	 * mismatch; when false such a fact is ignored, so the second document may
	 * document a curated subset of the first. A pattern that matches in neither
	 * document is always ignored.
	 */
	List<String> violations(Document first, Document second, boolean requireInBoth) {
		List<String> violations = new ArrayList<>();
		for (String pattern : patterns) {
			collect(pattern, first, second, requireInBoth, violations);
		}
		return violations;
	}

	private void collect(String pattern, Document first, Document second, boolean requireInBoth,
			List<String> violations) {
		Pattern compiled = Pattern.compile(pattern);
		try {
			collectCapturedMismatch(pattern, compiled, first, second, requireInBoth, violations);
		} catch (BacktrackLimitExceededException e) {
			violations.add("pattern '" + pattern
					+ "' could not be evaluated within its backtracking budget (possible catastrophic backtracking)");
		}
	}

	private void collectCapturedMismatch(String pattern, Pattern compiled, Document first, Document second,
			boolean requireInBoth, List<String> violations) {
		Optional<String> firstValue = capture(compiled, first.content());
		Optional<String> secondValue = capture(compiled, second.content());
		if (isIgnored(firstValue, secondValue, requireInBoth)) {
			return;
		}
		addMismatch(pattern, first, firstValue, second, secondValue, violations);
	}

	/**
	 * A pattern is ignored when it matches in neither document, or when it matches
	 * in only one and the caller does not require the fact in both.
	 */
	private boolean isIgnored(Optional<String> firstValue, Optional<String> secondValue, boolean requireInBoth) {
		if (firstValue.isEmpty() && secondValue.isEmpty()) {
			return true;
		}
		return absentFromOne(firstValue, secondValue) && !requireInBoth;
	}

	private boolean absentFromOne(Optional<String> firstValue, Optional<String> secondValue) {
		return firstValue.isEmpty() || secondValue.isEmpty();
	}

	private void addMismatch(String pattern, Document first, Optional<String> firstValue, Document second,
			Optional<String> secondValue, List<String> violations) {
		if (!firstValue.equals(secondValue)) {
			violations.add("pattern '" + pattern + "' captured " + describe(first, firstValue) + " but "
					+ describe(second, secondValue));
		}
	}

	private String describe(Document document, Optional<String> value) {
		return document.name() + "=" + value.map(captured -> "'" + captured + "'").orElse("<absent>");
	}

	private Optional<String> capture(Pattern pattern, String content) {
		Matcher matcher = pattern.matcher(new BoundedCharSequence(content, stepBudget(content)));
		return matcher.find() ? Optional.ofNullable(matcher.group(1)) : Optional.empty();
	}

	private long stepBudget(String content) {
		return Math.max(MINIMUM_STEPS, (long) content.length() * STEPS_PER_CHARACTER);
	}
}
