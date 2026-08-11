package io.github.adamw7.tools.enforcer.doc;

import java.io.File;
import java.util.List;

import org.apache.maven.enforcer.rule.api.EnforcerRuleException;

import io.github.adamw7.tools.enforcer.rule.ClaudeCodeEnforcerRule;

/**
 * Base for the rules that hold one document against another, by comparing what a
 * list of single-group regular expressions captures from each. Both documents must
 * exist, because a rule pointed at a file that is not there is a build-setup
 * mistake; every disagreement is then reported together.
 * <p>
 * The two rules built on this are the same check with one flag between them —
 * whether a fact stated in only one of the documents is a violation — so what they
 * each supply is their pair of file parameters and the vocabulary in
 * {@link Comparison}. The comparison itself lives in {@link DocumentConsistency}.
 */
abstract class ConsistencyRule extends ClaudeCodeEnforcerRule {

	/**
	 * How a rule names the pair it compares, and what it makes of a fact only one
	 * of them states. The four values are one thing — the vocabulary of a
	 * comparison — so they are supplied together rather than as four positional
	 * arguments a reader has to count.
	 *
	 * @param firstParameter  the configuration parameter naming the first document,
	 *                        e.g. {@code claudeMdFile}
	 * @param secondParameter the configuration parameter naming the second document,
	 *                        e.g. {@code agentsMdFile}
	 * @param header          the header prefixing the grouped report
	 * @param requireInBoth   whether a fact present in one document and absent from
	 *                        the other is a violation: mirror documents require it in
	 *                        both, while a curated view may simply not repeat it
	 */
	record Comparison(String firstParameter, String secondParameter, String header, boolean requireInBoth) {
	}

	private final Comparison comparison;

	/** Regular expressions, each with one capturing group, whose captured value must agree. */
	private List<String> consistentPatterns;

	protected ConsistencyRule(Comparison comparison) {
		this.comparison = comparison;
	}

	@Override
	public final void execute() throws EnforcerRuleException {
		File first = firstFile();
		File second = secondFile();
		requireDocument(first, comparison.firstParameter());
		requireDocument(second, comparison.secondParameter());
		report(comparison.header(), new DocumentConsistency(consistentPatterns, comparison.requireInBoth())
				.violations(first, second));
	}

	/** The document a violation message names first. Injected from the rule configuration. */
	protected abstract File firstFile();

	/** The document a violation message names second. Injected from the rule configuration. */
	protected abstract File secondFile();

	void setConsistentPatterns(List<String> consistentPatterns) {
		this.consistentPatterns = consistentPatterns;
	}
}
