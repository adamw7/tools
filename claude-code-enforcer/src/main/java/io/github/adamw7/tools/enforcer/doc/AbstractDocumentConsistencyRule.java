package io.github.adamw7.tools.enforcer.doc;

import java.io.File;
import java.util.List;

import org.apache.maven.enforcer.rule.api.EnforcerRuleException;

import io.github.adamw7.tools.enforcer.doc.DocumentConsistency.Document;
import io.github.adamw7.tools.enforcer.rule.ClaudeCodeEnforcerRule;
import io.github.adamw7.tools.enforcer.text.MarkdownText;

/**
 * Base for the enforcer rules that compare two documents against a list of
 * single-group regular expressions and fail when a captured value disagrees.
 * Both {@link CrossDocConsistencyRule} and {@link ReadmeConsistencyRule} share
 * the same shape: validate that both files are configured and present, verify
 * the patterns declare a capturing group, then report the mismatches through the
 * common {@link #report} path. This class owns that flow so a subclass only names
 * its two files and chooses two behaviours.
 * <p>
 * Subclasses differ in exactly two ways: whether a fact present in only one
 * document is a mismatch (mirror documents require it in both; a curated view
 * does not), captured by {@link #requireInBoth()}, and the header shown in the
 * report, captured by {@link #reportHeader()}. Because the maven-enforcer plugin
 * injects configuration into fields named after the XML elements, each subclass
 * keeps its own descriptively named file fields and exposes them here.
 */
abstract class AbstractDocumentConsistencyRule extends ClaudeCodeEnforcerRule {

	@Override
	public final void execute() throws EnforcerRuleException {
		requireConfigured(firstFile(), firstParameterName());
		requireExists(firstFile(), firstParameterName());
		requireConfigured(secondFile(), secondParameterName());
		requireExists(secondFile(), secondParameterName());
		DocumentConsistency consistency = new DocumentConsistency(consistentPatterns());
		consistency.verifyPatterns();
		Document first = document(firstFile());
		Document second = document(secondFile());
		report(reportHeader(), consistency.violations(first, second, requireInBoth()));
	}

	private Document document(File file) {
		return new Document(file.getName(), MarkdownText.read(file, file.getName()));
	}

	/** The first document to compare. Injected from the rule configuration. */
	protected abstract File firstFile();

	/** The configuration parameter name for the first file, used in messages. */
	protected abstract String firstParameterName();

	/** The second document to compare. Injected from the rule configuration. */
	protected abstract File secondFile();

	/** The configuration parameter name for the second file, used in messages. */
	protected abstract String secondParameterName();

	/** The single-group regular expressions whose captured values must agree. */
	protected abstract List<String> consistentPatterns();

	/**
	 * Whether a fact present in only one document is a mismatch. Mirror documents
	 * return {@code true}; a curated view that may document a subset returns
	 * {@code false}.
	 */
	protected abstract boolean requireInBoth();

	/** The header that prefixes the grouped violation report. */
	protected abstract String reportHeader();
}
