package io.github.adamw7.tools.enforcer.rule;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.maven.enforcer.rule.api.EnforcerRuleException;

import io.github.adamw7.tools.enforcer.text.MarkdownDocument;

/**
 * Base for enforcer rules that validate a Markdown document follows an expected
 * structure: it must exist, be non-empty, start with a required title heading
 * (a leading UTF-8 BOM is tolerated), and contain every required section
 * heading as a real, non-empty heading.
 * <p>
 * Headings are matched on whole lines outside fenced code blocks, so a heading
 * mentioned inside a {@code ```} fence or in prose does not satisfy a
 * requirement, and a partial match such as {@code # CLAUDE.md-extended} does
 * not satisfy {@code # CLAUDE.md}. All structural problems are collected and
 * reported together rather than one per build.
 * <p>
 * Beyond the mandatory structure, several optional checks can be switched on
 * from the rule configuration: a list of {@code forbiddenTokens} that must not
 * appear outside code fences, {@code enforceSectionOrder} to require the
 * sections in the configured order, a {@code maxLineLength} cap, and
 * {@code validateFileReferences} to confirm that Markdown links to local files
 * resolve to something on disk. Each is disabled by default, so existing
 * configurations are unaffected.
 * <p>
 * The required-section check can also be switched off entirely with
 * {@code enforceRequiredSections}, leaving only the mandatory structure (exists,
 * non-empty, title heading). That lets the rule guard a document whose section
 * layout is not fixed — for example a {@code CLAUDE.md} generated afresh for an
 * arbitrary adopted repository, where only the title is predictable.
 * <p>
 * The title and required sections default to the subclass-provided values but
 * can be overridden from the rule configuration, so the rule is reusable across
 * projects without a recompile. Subclasses contribute the file, its name, the
 * defaults, and any document-specific checks.
 */
public abstract class MarkdownFormatRule extends ClaudeCodeEnforcerRule {

	private static final Pattern MARKDOWN_LINK = Pattern.compile("\\[[^\\]]*\\]\\(([^)]+)\\)");
	private static final Pattern EXTERNAL_REFERENCE = Pattern.compile("^[a-zA-Z][a-zA-Z0-9+.-]*:.*");

	/** Optional override for the title heading. Falls back to the subclass default. */
	private String titleHeading;

	/** Optional override for the required sections. Falls back to the subclass default. */
	private List<String> requiredSections;

	/** When false, the required-section check is skipped and only the title structure is enforced. */
	private boolean enforceRequiredSections = true;

	/** Optional tokens that must not appear outside fenced code blocks. */
	private List<String> forbiddenTokens;

	/** When true, the required sections must appear in the configured order. */
	private boolean enforceSectionOrder;

	/** Maximum allowed line length outside code fences. Zero (default) disables the check. */
	private int maxLineLength;

	/** When true, Markdown links to local files must resolve to an existing file. */
	private boolean validateFileReferences;

	/** Base directory for resolving relative file references. Defaults to the document's directory. */
	private File referenceBaseDir;

	@Override
	public void execute() throws EnforcerRuleException {
		MarkdownDocument document = readDocument();
		List<String> violations = new ArrayList<>();
		collectTitleViolation(document, violations);
		collectSectionViolations(document, violations);
		collectOrderViolations(document, violations);
		collectForbiddenTokenViolations(document, violations);
		collectLineLengthViolations(document, violations);
		collectFileReferenceViolations(document, violations);
		collectAdditionalViolations(document, violations);
		report(documentName() + " is not well formed:", violations);
	}

	/** The file to validate. Injected from the rule configuration. */
	protected abstract File documentFile();

	/** Human-readable file name used in messages, e.g. {@code CLAUDE.md}. */
	protected abstract String documentName();

	/** The default title heading the document must start with, e.g. {@code # CLAUDE.md}. */
	protected abstract String defaultTitleHeading();

	/** The default section headings the document must contain. */
	protected abstract List<String> defaultRequiredSections();

	/** Hook for document-specific checks. The default implementation does nothing. */
	protected void collectAdditionalViolations(MarkdownDocument document, List<String> violations) {
	}

	@Override
	protected List<String> howToFix() {
		return List.of(
				"Open " + documentName() + " and make its first non-blank line the '" + titleHeading() + "' title heading.",
				"Add every missing section heading listed above, each with non-empty content beneath it.",
				"Resolve any remaining item — section order, forbidden tokens, line length, or broken file links.",
				"Re-run the build to confirm " + documentName() + " is well formed.");
	}

	final String titleHeading() {
		return titleHeading != null ? titleHeading : defaultTitleHeading();
	}

	final List<String> requiredSections() {
		return requiredSections != null ? requiredSections : defaultRequiredSections();
	}

	public void setTitleHeading(String titleHeading) {
		this.titleHeading = titleHeading;
	}

	public void setRequiredSections(List<String> requiredSections) {
		this.requiredSections = requiredSections;
	}

	public void setEnforceRequiredSections(boolean enforceRequiredSections) {
		this.enforceRequiredSections = enforceRequiredSections;
	}

	public void setForbiddenTokens(List<String> forbiddenTokens) {
		this.forbiddenTokens = forbiddenTokens;
	}

	public void setEnforceSectionOrder(boolean enforceSectionOrder) {
		this.enforceSectionOrder = enforceSectionOrder;
	}

	public void setMaxLineLength(int maxLineLength) {
		this.maxLineLength = maxLineLength;
	}

	public void setValidateFileReferences(boolean validateFileReferences) {
		this.validateFileReferences = validateFileReferences;
	}

	public void setReferenceBaseDir(File referenceBaseDir) {
		this.referenceBaseDir = referenceBaseDir;
	}

	private MarkdownDocument readDocument() throws EnforcerRuleException {
		File file = documentFile();
		requireConfigured(file, documentName());
		requireExists(file, documentName());
		return MarkdownDocument.parse(requireContent(file, documentName()));
	}

	private void collectTitleViolation(MarkdownDocument document, List<String> violations) {
		if (!document.firstNonBlankLine().equals(titleHeading())) {
			violations.add(documentName() + " must start with the '" + titleHeading() + "' title heading");
		}
	}

	private void collectSectionViolations(MarkdownDocument document, List<String> violations) {
		if (!enforceRequiredSections) {
			return;
		}
		for (String section : requiredSections()) {
			addSectionViolation(document, section, violations);
		}
	}

	private void addSectionViolation(MarkdownDocument document, String section, List<String> violations) {
		if (!document.hasHeading(section)) {
			violations.add(documentName() + " is missing required section heading: " + section);
		} else if (!document.hasBody(section)) {
			violations.add(documentName() + " has an empty section: " + section);
		}
	}

	/**
	 * When ordering is enforced, the required sections that are present must appear
	 * in the same relative order as configured. Absent sections are already
	 * reported by the section check, so only the present ones are compared here.
	 */
	private void collectOrderViolations(MarkdownDocument document, List<String> violations) {
		if (!enforceSectionOrder || !enforceRequiredSections) {
			return;
		}
		List<String> expected = presentRequiredSections(document);
		List<String> actual = document.headingsInOrder(requiredSections());
		if (!actual.equals(expected)) {
			violations.add(documentName() + " sections are out of order; expected " + expected + " but found " + actual);
		}
	}

	private List<String> presentRequiredSections(MarkdownDocument document) {
		Set<String> headings = document.headings();
		return requiredSections().stream().filter(headings::contains).toList();
	}

	private void collectForbiddenTokenViolations(MarkdownDocument document, List<String> violations) {
		if (forbiddenTokens == null) {
			return;
		}
		for (String token : forbiddenTokens) {
			if (document.containsOutsideFences(token)) {
				violations.add(documentName() + " must not contain forbidden token: " + token);
			}
		}
	}

	private void collectLineLengthViolations(MarkdownDocument document, List<String> violations) {
		if (maxLineLength <= 0) {
			return;
		}
		for (int i = 0; i < document.lineCount(); i++) {
			addLineLengthViolation(document.line(i), document.isInsideFence(i), i, violations);
		}
	}

	private void addLineLengthViolation(String line, boolean insideFence, int index, List<String> violations) {
		if (!insideFence && line.length() > maxLineLength) {
			violations.add(documentName() + " line " + (index + 1) + " exceeds " + maxLineLength
					+ " characters (" + line.length() + ")");
		}
	}

	private void collectFileReferenceViolations(MarkdownDocument document, List<String> violations) {
		if (!validateFileReferences) {
			return;
		}
		File baseDir = referenceBaseDir();
		for (int i = 0; i < document.lineCount(); i++) {
			collectLineReferences(document.line(i), document.isInsideFence(i), baseDir, violations);
		}
	}

	private void collectLineReferences(String line, boolean insideFence, File baseDir, List<String> violations) {
		if (insideFence) {
			return;
		}
		Matcher matcher = MARKDOWN_LINK.matcher(line);
		while (matcher.find()) {
			addReferenceViolation(linkDestination(matcher.group(1)), baseDir, violations);
		}
	}

	/**
	 * The destination part of a Markdown link target, dropping the optional title
	 * and any {@code <...>} wrapping, so {@code [t](file.md "Title")} resolves to
	 * {@code file.md} rather than the whole {@code file.md "Title"} string.
	 */
	private String linkDestination(String rawTarget) {
		String target = rawTarget.strip();
		if (target.startsWith("<") && target.contains(">")) {
			return target.substring(1, target.indexOf('>')).strip();
		}
		return target.split("\\s", 2)[0].strip();
	}

	private void addReferenceViolation(String target, File baseDir, List<String> violations) {
		String localPath = localReferencePath(target);
		if (localPath != null && !new File(baseDir, localPath).exists()) {
			violations.add(documentName() + " references a missing file: " + localPath);
		}
	}

	/** The on-disk path a link points to, or null when the link is external or an anchor. */
	private String localReferencePath(String target) {
		if (target.isEmpty() || target.startsWith("#") || EXTERNAL_REFERENCE.matcher(target).matches()) {
			return null;
		}
		String withoutAnchor = stripAfter(stripAfter(target, '#'), '?');
		return withoutAnchor.isEmpty() ? null : withoutAnchor;
	}

	private String stripAfter(String value, char delimiter) {
		int index = value.indexOf(delimiter);
		return index < 0 ? value : value.substring(0, index);
	}

	private File referenceBaseDir() {
		if (referenceBaseDir != null) {
			return referenceBaseDir;
		}
		File parent = documentFile().getAbsoluteFile().getParentFile();
		return parent != null ? parent : new File(".");
	}
}
