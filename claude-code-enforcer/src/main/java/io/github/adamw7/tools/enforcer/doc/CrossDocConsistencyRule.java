package io.github.adamw7.tools.enforcer.doc;

import java.io.File;

import javax.inject.Named;

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
public class CrossDocConsistencyRule extends ConsistencyRule {

	/** The first document to compare. Injected from the rule configuration. */
	private File claudeMdFile;

	/** The second document to compare. Injected from the rule configuration. */
	private File agentsMdFile;

	/** These are mirror documents, so a fact one states and the other omits is a mismatch. */
	public CrossDocConsistencyRule() {
		super(new Comparison("claudeMdFile", "agentsMdFile", "Documents are inconsistent:", true));
	}

	@Override
	protected File firstFile() {
		return claudeMdFile;
	}

	@Override
	protected File secondFile() {
		return agentsMdFile;
	}

	void setClaudeMdFile(File claudeMdFile) {
		this.claudeMdFile = claudeMdFile;
	}

	void setAgentsMdFile(File agentsMdFile) {
		this.agentsMdFile = agentsMdFile;
	}
}
