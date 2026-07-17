package io.github.adamw7.tools.enforcer.doc;

import java.io.File;
import java.util.List;

import javax.inject.Named;

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
public class ReadmeConsistencyRule extends AbstractDocumentConsistencyRule {

	/** The README under review. Injected from the rule configuration. */
	private File readmeFile;

	/** The agent docs treated as the source of truth. Injected from the rule configuration. */
	private File agentDocFile;

	/** Regular expressions, each with one capturing group, whose captured value must agree. */
	private List<String> consistentPatterns;

	@Override
	protected File firstFile() {
		return readmeFile;
	}

	@Override
	protected String firstParameterName() {
		return "readmeFile";
	}

	@Override
	protected File secondFile() {
		return agentDocFile;
	}

	@Override
	protected String secondParameterName() {
		return "agentDocFile";
	}

	@Override
	protected List<String> consistentPatterns() {
		return consistentPatterns;
	}

	@Override
	protected boolean requireInBoth() {
		return false;
	}

	@Override
	protected String reportHeader() {
		return "README has drifted from the agent docs:";
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
