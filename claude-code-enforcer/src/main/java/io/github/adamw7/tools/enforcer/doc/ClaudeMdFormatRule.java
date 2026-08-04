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

	public ClaudeMdFormatRule() {
		super("CLAUDE.md", REQUIRED_SECTIONS);
	}

	/**
	 * The rule configured exactly as a build wires it — the file to check and
	 * nothing else — for a caller outside this package that has to prove a
	 * {@code CLAUDE.md} it produced satisfies the check it is about to wire in.
	 * The adopt module's conformer is written against this contract and duplicates
	 * its constants, so its tests run the real rule over the real output rather
	 * than trusting a copy to have stayed in step.
	 *
	 * @param claudeMdFile the {@code CLAUDE.md} to validate
	 * @return a rule that validates {@code claudeMdFile} with the default title and
	 *         required sections, every optional check left off
	 */
	public static ClaudeMdFormatRule validating(File claudeMdFile) {
		ClaudeMdFormatRule rule = new ClaudeMdFormatRule();
		rule.setClaudeMdFile(claudeMdFile);
		return rule;
	}

	@Override
	protected File documentFile() {
		return claudeMdFile;
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
}
