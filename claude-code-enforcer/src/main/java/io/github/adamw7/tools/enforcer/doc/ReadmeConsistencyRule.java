package io.github.adamw7.tools.enforcer.doc;

import java.io.File;
import java.util.List;

import javax.inject.Named;

import org.apache.maven.enforcer.rule.api.EnforcerRuleException;

import io.github.adamw7.tools.enforcer.rule.ClaudeCodeEnforcerRule;

/**
 * Enforcer rule that keeps the README from drifting away from the agent docs.
 * {@code README.md} is a curated, example-heavy view of the same project that
 * {@code AGENTS.md} (the single source of truth) describes, so any capability or
 * version it documents must match. Each configured pattern is a regular expression
 * with one capturing group, captured from each file and compared.
 * <p>
 * Unlike {@link CrossDocConsistencyRule}, a fact the README simply does not repeat
 * is not a violation: only a value present in both files that disagrees fails the
 * build. For example the pattern {@code proto(\d)} pins the supported protobuf
 * version. All mismatches are reported together.
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
		requireDocument(readmeFile, "readmeFile");
		requireDocument(agentDocFile, "agentDocFile");
		report("README has drifted from the agent docs:",
				new DocumentConsistency(consistentPatterns, false).violations(readmeFile, agentDocFile));
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
}
