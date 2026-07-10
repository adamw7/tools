package io.github.adamw7.tools.enforcer.doc;

import java.io.File;
import java.util.List;

import javax.inject.Named;

import org.apache.maven.enforcer.rule.api.EnforcerRuleException;

import io.github.adamw7.tools.enforcer.doc.DocumentConsistency.Document;
import io.github.adamw7.tools.enforcer.rule.ClaudeCodeEnforcerRule;
import io.github.adamw7.tools.enforcer.text.MarkdownText;

/**
 * Enforcer rule that keeps the README from drifting away from the agent docs.
 * {@code README.md} is a curated, example-heavy view of the same project that
 * {@code AGENTS.md} (the single source of truth) describes, so any capability or
 * version it documents must match. Each configured pattern is a regular
 * expression with one capturing group; the rule captures that group from each
 * file and fails when the README's value disagrees with the agent docs.
 * <p>
 * Unlike {@link CrossDocConsistencyRule}, a fact the README simply does not
 * repeat is not a violation: the README is allowed to document a subset. Only a
 * value present in both files that disagrees fails the build. For example the
 * pattern {@code proto(\d)} pins the supported protobuf version: if the README
 * claims {@code proto3} support while the agent docs say {@code proto2}, the
 * build fails, but a fact the README omits is ignored. All mismatches are
 * reported together.
 */
@Named("readmeConsistency")
public class ReadmeConsistencyRule extends ClaudeCodeEnforcerRule {

	/** The README under review. Injected from the rule configuration. */
	private File readmeFile;

	/** The agent docs treated as the source of truth. Injected from the rule configuration. */
	private File agentDocFile;

	/** Regular expressions, each with one capturing group, whose captured value must agree. */
	private List<String> consistentPatterns;

	@Override
	public void execute() throws EnforcerRuleException {
		verifyConfigured();
		DocumentConsistency consistency = new DocumentConsistency(consistentPatterns);
		consistency.verifyPatterns();
		Document readme = document(readmeFile);
		Document agentDoc = document(agentDocFile);
		report("README has drifted from the agent docs:", consistency.violations(readme, agentDoc, false));
	}

	private Document document(File file) {
		return new Document(file.getName(), MarkdownText.read(file, file.getName()));
	}

	private void verifyConfigured() throws EnforcerRuleException {
		verifyConfigured(readmeFile, "readmeFile");
		verifyConfigured(agentDocFile, "agentDocFile");
	}

	private void verifyConfigured(File file, String parameter) throws EnforcerRuleException {
		if (file == null) {
			throw new EnforcerRuleException("The " + parameter + " parameter is not configured");
		}
		if (!file.isFile()) {
			throw new EnforcerRuleException(parameter + " does not exist at " + file);
		}
	}

	void setReadmeFile(File readmeFile) {
		this.readmeFile = readmeFile;
	}

	void setAgentDocFile(File agentDocFile) {
		this.agentDocFile = agentDocFile;
	}

	void setConsistentPatterns(List<String> consistentPatterns) {
		this.consistentPatterns = consistentPatterns;
	}

	@Override
	public String toString() {
		return String.format("ReadmeConsistencyRule[readmeFile=%s, agentDocFile=%s]", readmeFile, agentDocFile);
	}
}
