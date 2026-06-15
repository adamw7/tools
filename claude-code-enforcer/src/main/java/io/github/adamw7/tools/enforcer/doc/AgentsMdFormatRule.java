package io.github.adamw7.tools.enforcer.doc;

import java.io.File;
import java.util.List;

import javax.inject.Named;

import io.github.adamw7.tools.enforcer.rule.MarkdownFormatRule;

/**
 * Enforcer rule that fails the build when {@code AGENTS.md} is missing or does
 * not follow the expected structure: it must start with the {@code # AGENTS.md}
 * title and contain every required section heading. {@code CLAUDE.md} defers to
 * {@code AGENTS.md} as the single source of truth, so this rule keeps that
 * source intact.
 */
@Named("agentsMdFormat")
public class AgentsMdFormatRule extends MarkdownFormatRule {

	private static final String DOCUMENT_NAME = "AGENTS.md";
	private static final String TITLE_HEADING = "# AGENTS.md";
	private static final List<String> REQUIRED_SECTIONS = List.of(
			"## Project overview",
			"## Module layout",
			"## Environment & toolchain",
			"## Build, test, and run",
			"## Code style & conventions",
			"## Releasing",
			"## Pull requests & commits");

	/** The {@code AGENTS.md} file to validate. Injected from the rule configuration. */
	private File agentsMdFile;

	@Override
	protected File documentFile() {
		return agentsMdFile;
	}

	@Override
	protected String documentName() {
		return DOCUMENT_NAME;
	}

	@Override
	protected String defaultTitleHeading() {
		return TITLE_HEADING;
	}

	@Override
	protected List<String> defaultRequiredSections() {
		return REQUIRED_SECTIONS;
	}

	void setAgentsMdFile(File agentsMdFile) {
		this.agentsMdFile = agentsMdFile;
	}

	@Override
	public String toString() {
		return String.format("AgentsMdFormatRule[agentsMdFile=%s]", agentsMdFile);
	}
}
