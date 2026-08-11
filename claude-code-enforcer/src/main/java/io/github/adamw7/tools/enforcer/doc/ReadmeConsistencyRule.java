package io.github.adamw7.tools.enforcer.doc;

import java.io.File;

import javax.inject.Named;

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
public class ReadmeConsistencyRule extends ConsistencyRule {

	/** The README under review. Injected from the rule configuration. */
	private File readmeFile;

	/** The agent docs treated as the source of truth. Injected from the rule configuration. */
	private File agentDocFile;

	/** A curated view may leave a fact out, so only a value present in both and disagreeing fails. */
	public ReadmeConsistencyRule() {
		super(new Comparison("readmeFile", "agentDocFile", "README has drifted from the agent docs:", false));
	}

	@Override
	protected File firstFile() {
		return readmeFile;
	}

	@Override
	protected File secondFile() {
		return agentDocFile;
	}

	void setReadmeFile(File readmeFile) {
		this.readmeFile = readmeFile;
	}

	void setAgentDocFile(File agentDocFile) {
		this.agentDocFile = agentDocFile;
	}
}
