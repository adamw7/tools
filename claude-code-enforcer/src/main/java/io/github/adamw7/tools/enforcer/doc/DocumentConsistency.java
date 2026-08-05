package io.github.adamw7.tools.enforcer.doc;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.maven.enforcer.rule.api.EnforcerRuleException;

import io.github.adamw7.tools.enforcer.doc.BoundedCharSequence.BacktrackLimitExceededException;
import io.github.adamw7.tools.enforcer.rule.Patterns;
import io.github.adamw7.tools.enforcer.text.MarkdownText;

/**
 * Compares two documents against a list of single-group regular expressions and
 * reports where a captured value differs. Shared by
 * {@link CrossDocConsistencyRule} and {@link ReadmeConsistencyRule} so the pattern
 * validation and capture logic live in one place.
 * <p>
 * The two rules differ only in how they treat a fact that appears in one document
 * but not the other: mirror documents (CLAUDE.md and AGENTS.md) require it in
 * both, while a curated view (README.md against the agent docs) ignores a fact the
 * view simply chose not to repeat. That choice is the {@code requireInBoth} flag.
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

	private static final String PARAMETER = "consistentPattern";

	/** A document to compare: its content, and the name shown in violation messages. */
	private record Document(String name, String content) {
	}

	private final List<String> patterns;
	private final boolean requireInBoth;

	DocumentConsistency(List<String> patterns, boolean requireInBoth) {
		this.patterns = patterns != null ? patterns : List.of();
		this.requireInBoth = requireInBoth;
	}

	/**
	 * Collects one violation per pattern whose captured values disagree. A pattern
	 * that matches in neither document is ignored, as is one that matches in only
	 * one document unless {@code requireInBoth} was set.
	 */
	List<String> violations(File firstFile, File secondFile) throws EnforcerRuleException {
		List<Pattern> compiled = compilePatterns();
		Document first = read(firstFile);
		Document second = read(secondFile);
		List<String> violations = new ArrayList<>();
		for (Pattern pattern : compiled) {
			collect(pattern, first, second, violations);
		}
		return violations;
	}

	/**
	 * Each pattern must declare a capturing group, since comparison reads
	 * {@code group(1)}. That is a build-setup mistake, so it fails with a message
	 * naming the pattern instead of letting an opaque
	 * {@link IndexOutOfBoundsException} escape as an internal build error — as
	 * {@link Patterns} does for a pattern that does not compile at all.
	 */
	private List<Pattern> compilePatterns() throws EnforcerRuleException {
		List<Pattern> compiled = Patterns.compileAll(patterns, PARAMETER);
		for (Pattern pattern : compiled) {
			requireCapturingGroup(pattern);
		}
		return compiled;
	}

	private void requireCapturingGroup(Pattern pattern) throws EnforcerRuleException {
		if (pattern.matcher("").groupCount() < 1) {
			throw new EnforcerRuleException(
					PARAMETER + " '" + pattern.pattern() + "' must declare a capturing group");
		}
	}

	private Document read(File file) {
		return new Document(file.getName(), MarkdownText.read(file, file.getName()));
	}

	private void collect(Pattern pattern, Document first, Document second, List<String> violations) {
		try {
			addMismatch(pattern, first, second, violations);
		} catch (BacktrackLimitExceededException e) {
			violations.add("pattern '" + pattern.pattern()
					+ "' could not be evaluated within its backtracking budget (possible catastrophic backtracking)");
		}
	}

	private void addMismatch(Pattern pattern, Document first, Document second, List<String> violations) {
		Optional<String> firstValue = capture(pattern, first.content());
		Optional<String> secondValue = capture(pattern, second.content());
		if (!firstValue.equals(secondValue) && !isIgnored(firstValue, secondValue)) {
			violations.add("pattern '" + pattern.pattern() + "' captured " + describe(first, firstValue) + " but "
					+ describe(second, secondValue));
		}
	}

	/** A fact present in only one document is a mismatch only when required in both. */
	private boolean isIgnored(Optional<String> firstValue, Optional<String> secondValue) {
		return (firstValue.isEmpty() || secondValue.isEmpty()) && !requireInBoth;
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
