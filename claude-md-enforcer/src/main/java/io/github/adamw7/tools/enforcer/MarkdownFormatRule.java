package io.github.adamw7.tools.enforcer;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import org.apache.maven.enforcer.rule.api.AbstractEnforcerRule;
import org.apache.maven.enforcer.rule.api.EnforcerRuleException;

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
 * The title and required sections default to the subclass-provided values but
 * can be overridden from the rule configuration, so the rule is reusable across
 * projects without a recompile. Subclasses contribute the file, its name, the
 * defaults, and any document-specific checks.
 */
abstract class MarkdownFormatRule extends AbstractEnforcerRule {

	private static final String CODE_FENCE = "```";
	private static final String HEADING_PREFIX = "#";

	/** Optional override for the title heading. Falls back to the subclass default. */
	private String titleHeading;

	/** Optional override for the required sections. Falls back to the subclass default. */
	private List<String> requiredSections;

	@Override
	public void execute() throws EnforcerRuleException {
		String content = readContent();
		List<String> violations = new ArrayList<>();
		collectTitleViolation(content, violations);
		collectSectionViolations(content, violations);
		collectAdditionalViolations(content, violations);
		failIfAny(violations);
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
	protected void collectAdditionalViolations(String content, List<String> violations) {
	}

	/** True when {@code token} appears on a line outside a fenced code block. */
	protected final boolean containsOutsideCodeFences(String content, String token) {
		boolean insideCodeFence = false;
		for (String line : lines(content)) {
			if (line.strip().startsWith(CODE_FENCE)) {
				insideCodeFence = !insideCodeFence;
			} else if (!insideCodeFence && line.contains(token)) {
				return true;
			}
		}
		return false;
	}

	final String titleHeading() {
		return titleHeading != null ? titleHeading : defaultTitleHeading();
	}

	final List<String> requiredSections() {
		return requiredSections != null ? requiredSections : defaultRequiredSections();
	}

	void setTitleHeading(String titleHeading) {
		this.titleHeading = titleHeading;
	}

	void setRequiredSections(List<String> requiredSections) {
		this.requiredSections = requiredSections;
	}

	private String readContent() throws EnforcerRuleException {
		File file = documentFile();
		if (file == null) {
			throw new EnforcerRuleException("The " + documentName() + " file parameter is not configured");
		}
		if (!file.isFile()) {
			throw new EnforcerRuleException(documentName() + " does not exist at " + file);
		}
		String content = MarkdownText.stripByteOrderMark(readAll(file));
		if (content.isBlank()) {
			throw new EnforcerRuleException(documentName() + " is empty: " + file);
		}
		return content;
	}

	private String readAll(File file) throws EnforcerRuleException {
		try {
			return Files.readString(file.toPath());
		} catch (IOException e) {
			throw new EnforcerRuleException("Could not read " + documentName() + " at " + file, e);
		}
	}

	private void collectTitleViolation(String content, List<String> violations) {
		if (!MarkdownText.firstNonBlankLine(content).equals(titleHeading())) {
			violations.add(documentName() + " must start with the '" + titleHeading() + "' title heading");
		}
	}

	private void collectSectionViolations(String content, List<String> violations) {
		Set<String> headings = headings(content);
		List<String> lines = lines(content);
		for (String section : requiredSections()) {
			addSectionViolation(lines, headings, section, violations);
		}
	}

	private void addSectionViolation(List<String> lines, Set<String> headings, String section,
			List<String> violations) {
		if (!headings.contains(section)) {
			violations.add(documentName() + " is missing required section heading: " + section);
		} else if (!hasBody(lines, headingIndex(lines, section))) {
			violations.add(documentName() + " has an empty section: " + section);
		}
	}

	private Set<String> headings(String content) {
		Set<String> headings = new LinkedHashSet<>();
		boolean insideCodeFence = false;
		for (String line : lines(content)) {
			insideCodeFence = collectHeading(line, insideCodeFence, headings);
		}
		return headings;
	}

	private boolean collectHeading(String line, boolean insideCodeFence, Set<String> headings) {
		String trimmed = line.strip();
		if (trimmed.startsWith(CODE_FENCE)) {
			return !insideCodeFence;
		}
		if (!insideCodeFence && isHeading(trimmed)) {
			headings.add(trimmed);
		}
		return insideCodeFence;
	}

	private int headingIndex(List<String> lines, String section) {
		for (int i = 0; i < lines.size(); i++) {
			if (lines.get(i).strip().equals(section)) {
				return i;
			}
		}
		return -1;
	}

	private boolean hasBody(List<String> lines, int headingIndex) {
		for (int i = headingIndex + 1; i < lines.size(); i++) {
			String line = lines.get(i).strip();
			if (line.startsWith(CODE_FENCE)) {
				return true;
			}
			if (isHeading(line)) {
				return false;
			}
			if (!line.isEmpty()) {
				return true;
			}
		}
		return false;
	}

	private boolean isHeading(String line) {
		return line.startsWith(HEADING_PREFIX);
	}

	private List<String> lines(String content) {
		return content.lines().toList();
	}

	private void failIfAny(List<String> violations) throws EnforcerRuleException {
		if (!violations.isEmpty()) {
			String separator = System.lineSeparator() + "  - ";
			throw new EnforcerRuleException(
					documentName() + " is not well formed:" + separator + String.join(separator, violations));
		}
	}
}
