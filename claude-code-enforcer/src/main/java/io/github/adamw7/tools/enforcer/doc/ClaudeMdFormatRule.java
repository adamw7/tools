package io.github.adamw7.tools.enforcer.doc;

import java.io.File;
import java.util.List;

import javax.inject.Named;

import io.github.adamw7.tools.enforcer.rule.MarkdownFormatRule;
import io.github.adamw7.tools.enforcer.text.MarkdownDocument;

/**
 * Enforcer rule that fails the build when {@code CLAUDE.md} is missing or does
 * not follow the expected structure: it must start with the {@code # CLAUDE.md}
 * title, reference {@code AGENTS.md}, and contain every required section
 * heading.
 */
@Named("claudeMdFormat")
public class ClaudeMdFormatRule extends MarkdownFormatRule {

	private static final String DOCUMENT_NAME = "CLAUDE.md";
	private static final String TITLE_HEADING = "# CLAUDE.md";
	private static final String AGENTS_REFERENCE = "AGENTS.md";
	private static final List<String> REQUIRED_SECTIONS = List.of(
			"## Project",
			"## Java version",
			"## Maven",
			"## Principles for Java Development",
			"## Testing",
			"## Dependencies");

	/** The {@code CLAUDE.md} file to validate. Injected from the rule configuration. */
	private File claudeMdFile;

	@Override
	protected File documentFile() {
		return claudeMdFile;
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

	@Override
	protected void collectAdditionalViolations(MarkdownDocument document, List<String> violations) {
		if (!document.containsOutsideFences(AGENTS_REFERENCE)) {
			violations.add("CLAUDE.md must reference " + AGENTS_REFERENCE + " as the source of truth");
		}
	}

	void setClaudeMdFile(File claudeMdFile) {
		this.claudeMdFile = claudeMdFile;
	}

	@Override
	public String toString() {
		return String.format("ClaudeMdFormatRule[claudeMdFile=%s]", claudeMdFile);
	}
}
