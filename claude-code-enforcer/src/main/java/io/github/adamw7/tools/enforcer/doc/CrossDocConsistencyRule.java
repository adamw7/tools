package io.github.adamw7.tools.enforcer.doc;

import java.io.File;
import java.util.List;

import javax.inject.Named;

import org.apache.maven.enforcer.rule.api.EnforcerRuleException;

import io.github.adamw7.tools.enforcer.rule.ClaudeCodeEnforcerRule;

/**
 * Enforcer rule that keeps two documents from contradicting each other. Because
 * {@code CLAUDE.md} defers to {@code AGENTS.md} as the single source of truth, any
 * fact stated in both must agree.
 * <p>
 * Each configured pattern is a regular expression with one capturing group; the
 * rule captures that group from each file and fails when the captured values
 * differ, or when the fact appears in one file but not the other. For example the
 * pattern {@code Java (\d+)} pins the Java version. A pattern that matches in
 * neither file is ignored, so unrelated documents are unaffected; all mismatches
 * are reported together.
 */
@Named("crossDocConsistency")
public class CrossDocConsistencyRule extends ClaudeCodeEnforcerRule {

	/** The first document to compare. Injected from the rule configuration. */
	private File claudeMdFile;

	/** The second document to compare. Injected from the rule configuration. */
	private File agentsMdFile;

	/** Regular expressions, each with one capturing group, whose captured value must agree. */
	private List<String> consistentPatterns;

	@Override
	public void execute() throws EnforcerRuleException {
		requireDocument(claudeMdFile, "claudeMdFile");
		requireDocument(agentsMdFile, "agentsMdFile");
		report("Documents are inconsistent:",
				new DocumentConsistency(consistentPatterns, true).violations(claudeMdFile, agentsMdFile));
	}

	void setClaudeMdFile(File claudeMdFile) {
		this.claudeMdFile = claudeMdFile;
	}

	void setAgentsMdFile(File agentsMdFile) {
		this.agentsMdFile = agentsMdFile;
	}

	void setConsistentPatterns(List<String> consistentPatterns) {
		this.consistentPatterns = consistentPatterns;
	}

	@Override
	public String toString() {
		return String.format("CrossDocConsistencyRule[claudeMdFile=%s, agentsMdFile=%s]", claudeMdFile, agentsMdFile);
	}
}
