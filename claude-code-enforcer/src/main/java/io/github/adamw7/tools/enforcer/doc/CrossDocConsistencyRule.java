package io.github.adamw7.tools.enforcer.doc;

import java.io.File;
import java.util.List;

import javax.inject.Named;

/**
 * Enforcer rule that keeps two documents from contradicting each other. Because
 * {@code CLAUDE.md} defers to {@code AGENTS.md} as the single source of truth,
 * any fact stated in both must agree. Each configured pattern is a regular
 * expression with one capturing group; the rule captures that group from each
 * file and fails when the captured values differ, or when the fact appears in
 * one file but not the other.
 * <p>
 * For example the pattern {@code Java (\d+)} pins the Java version: if
 * {@code CLAUDE.md} says {@code Java 25} and {@code AGENTS.md} says
 * {@code Java 24}, the build fails. A pattern that matches in neither file is
 * ignored, so unrelated documents are unaffected. All mismatches are reported
 * together.
 */
@Named("crossDocConsistency")
public class CrossDocConsistencyRule extends AbstractDocumentConsistencyRule {

	/** The first document to compare. Injected from the rule configuration. */
	private File claudeMdFile;

	/** The second document to compare. Injected from the rule configuration. */
	private File agentsMdFile;

	/** Regular expressions, each with one capturing group, whose captured value must agree. */
	private List<String> consistentPatterns;

	@Override
	protected File firstFile() {
		return claudeMdFile;
	}

	@Override
	protected String firstParameterName() {
		return "claudeMdFile";
	}

	@Override
	protected File secondFile() {
		return agentsMdFile;
	}

	@Override
	protected String secondParameterName() {
		return "agentsMdFile";
	}

	@Override
	protected List<String> consistentPatterns() {
		return consistentPatterns;
	}

	@Override
	protected boolean requireInBoth() {
		return true;
	}

	@Override
	protected String reportHeader() {
		return "Documents are inconsistent:";
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
