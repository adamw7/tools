package io.github.adamw7.tools.enforcer.doc;

import java.io.File;
import java.util.List;

import javax.inject.Named;

import io.github.adamw7.tools.enforcer.rule.MarkdownFormatRule;
import io.github.adamw7.tools.markdown.MarkdownDocument;

/**
 * Enforcer rule that fails the build when {@code CLAUDE.md} is missing or does
 * not follow the expected structure: it must start with the {@code # CLAUDE.md}
 * title, reference {@code AGENTS.md}, and contain every required section
 * heading.
 *
 * <p>Every one of those is a default, not a fixture. The title and the required
 * sections are overridden through {@link MarkdownFormatRule}'s
 * {@code titleHeading} and {@code requiredSections}, and the companion document
 * this one must point at through {@link #setRequiredReference}. The defaults are
 * this repository's — a Java project built with Maven, keeping its detail in an
 * {@code AGENTS.md} — because a rule has to default to something; a project
 * arranged differently configures the three and keeps the structural checking.
 * Configuring {@code <requiredReference/>} empty is how a project that keeps no
 * companion document says so, which is the one thing overriding the section list
 * could not express.
 */
@Named("claudeMdFormat")
public class ClaudeMdFormatRule extends MarkdownFormatRule {

	/** The companion document a {@code CLAUDE.md} points at unless configured otherwise. */
	static final String DEFAULT_REQUIRED_REFERENCE = "AGENTS.md";

	private static final List<String> REQUIRED_SECTIONS = List.of(
			"## Project",
			"## Java version",
			"## Maven",
			"## Principles for Java Development",
			"## Testing",
			"## Dependencies");

	/** The {@code CLAUDE.md} file to validate. Injected from the rule configuration. */
	private File claudeMdFile;

	/**
	 * Optional override for the companion document {@code CLAUDE.md} must reference.
	 * Null falls back to {@value #DEFAULT_REQUIRED_REFERENCE}; configured empty, the
	 * check is dropped, for a project that keeps no such document.
	 */
	private String requiredReference;

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

	/**
	 * The reference is looked for in prose, so a {@code CLAUDE.md} whose only mention
	 * of the companion sits in a fenced sample or behind an HTML comment does not
	 * satisfy it — the same reading every heading here gets.
	 */
	@Override
	protected void collectAdditionalViolations(MarkdownDocument document, List<String> violations) {
		String reference = requiredReference();
		if (!reference.isEmpty() && !document.containsInProse(reference)) {
			violations.add("CLAUDE.md must reference " + reference + " as the source of truth");
		}
	}

	/**
	 * @return the companion document to require, empty when the project keeps none.
	 *         The base class reads this too, so a repair inserts the reference this
	 *         rule goes on to check for.
	 */
	@Override
	protected String requiredReference() {
		return requiredReference == null ? DEFAULT_REQUIRED_REFERENCE : requiredReference.strip();
	}

	public void setClaudeMdFile(File claudeMdFile) {
		this.claudeMdFile = claudeMdFile;
	}

	public void setRequiredReference(String requiredReference) {
		this.requiredReference = requiredReference;
	}
}
