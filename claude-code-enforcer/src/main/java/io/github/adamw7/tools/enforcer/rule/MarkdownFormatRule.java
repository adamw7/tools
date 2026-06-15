package io.github.adamw7.tools.enforcer.rule;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.maven.enforcer.rule.api.EnforcerRuleException;

import io.github.adamw7.tools.enforcer.text.MarkdownText;

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
 * The title and required sections default to the subclass-provided values but
 * can be overridden from the rule configuration, so the rule is reusable across
 * projects without a recompile. Subclasses contribute the file, its name, the
 * defaults, and any document-specific checks.
 */
public abstract class MarkdownFormatRule extends ClaudeCodeEnforcerRule {

	private static final String CODE_FENCE = "```";
	private static final String HEADING_PREFIX = "#";
	private static final char HEADING_CHAR = '#';
	private static final Pattern MARKDOWN_LINK = Pattern.compile("\\[[^\\]]*\\]\\(([^)]+)\\)");
	private static final Pattern EXTERNAL_REFERENCE = Pattern.compile("^[a-zA-Z][a-zA-Z0-9+.-]*:.*");

	/** Optional override for the title heading. Falls back to the subclass default. */
	private String titleHeading;

	/** Optional override for the required sections. Falls back to the subclass default. */
	private List<String> requiredSections;

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
		String content = readContent();
		List<String> violations = new ArrayList<>();
		collectTitleViolation(content, violations);
		collectSectionViolations(content, violations);
		collectOrderViolations(content, violations);
		collectForbiddenTokenViolations(content, violations);
		collectLineLengthViolations(content, violations);
		collectFileReferenceViolations(content, violations);
		collectAdditionalViolations(content, violations);
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

	public void setTitleHeading(String titleHeading) {
		this.titleHeading = titleHeading;
	}

	public void setRequiredSections(List<String> requiredSections) {
		this.requiredSections = requiredSections;
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

	private String readAll(File file) {
		try {
			return Files.readString(file.toPath());
		} catch (IOException e) {
			throw new UncheckedIOException("Could not read " + documentName() + " at " + file, e);
		}
	}

	private void collectTitleViolation(String content, List<String> violations) {
		if (!MarkdownText.firstNonBlankLine(content).equals(titleHeading())) {
			violations.add(documentName() + " must start with the '" + titleHeading() + "' title heading");
		}
	}

	private void collectSectionViolations(String content, List<String> violations) {
		List<String> lines = lines(content);
		boolean[] insideFence = fenceMask(lines);
		Set<String> headings = headings(lines, insideFence);
		for (String section : requiredSections()) {
			addSectionViolation(lines, insideFence, headings, section, violations);
		}
	}

	private void addSectionViolation(List<String> lines, boolean[] insideFence, Set<String> headings,
			String section, List<String> violations) {
		if (!headings.contains(section)) {
			violations.add(documentName() + " is missing required section heading: " + section);
		} else if (!hasBody(lines, insideFence, headingIndex(lines, insideFence, section))) {
			violations.add(documentName() + " has an empty section: " + section);
		}
	}

	/**
	 * When ordering is enforced, the required sections that are present must appear
	 * in the same relative order as configured. Absent sections are already
	 * reported by the section check, so only the present ones are compared here.
	 */
	private void collectOrderViolations(String content, List<String> violations) {
		if (!enforceSectionOrder) {
			return;
		}
		List<String> lines = lines(content);
		boolean[] insideFence = fenceMask(lines);
		Set<String> headings = headings(lines, insideFence);
		List<String> expected = presentRequiredSections(headings);
		List<String> actual = requiredHeadingsInOrder(lines, insideFence);
		if (!actual.equals(expected)) {
			violations.add(documentName() + " sections are out of order; expected " + expected + " but found " + actual);
		}
	}

	private List<String> presentRequiredSections(Set<String> headings) {
		return requiredSections().stream().filter(headings::contains).toList();
	}

	private List<String> requiredHeadingsInOrder(List<String> lines, boolean[] insideFence) {
		Set<String> required = new LinkedHashSet<>(requiredSections());
		List<String> ordered = new ArrayList<>();
		for (int i = 0; i < lines.size(); i++) {
			addIfRequiredHeading(lines.get(i).strip(), insideFence[i], required, ordered);
		}
		return ordered;
	}

	private void addIfRequiredHeading(String line, boolean insideFence, Set<String> required, List<String> ordered) {
		if (!insideFence && required.contains(line)) {
			ordered.add(line);
		}
	}

	private void collectForbiddenTokenViolations(String content, List<String> violations) {
		if (forbiddenTokens == null) {
			return;
		}
		for (String token : forbiddenTokens) {
			if (containsOutsideCodeFences(content, token)) {
				violations.add(documentName() + " must not contain forbidden token: " + token);
			}
		}
	}

	private void collectLineLengthViolations(String content, List<String> violations) {
		if (maxLineLength <= 0) {
			return;
		}
		List<String> lines = lines(content);
		boolean[] insideFence = fenceMask(lines);
		for (int i = 0; i < lines.size(); i++) {
			addLineLengthViolation(lines.get(i), insideFence[i], i, violations);
		}
	}

	private void addLineLengthViolation(String line, boolean insideFence, int index, List<String> violations) {
		if (!insideFence && line.length() > maxLineLength) {
			violations.add(documentName() + " line " + (index + 1) + " exceeds " + maxLineLength
					+ " characters (" + line.length() + ")");
		}
	}

	private void collectFileReferenceViolations(String content, List<String> violations) {
		if (!validateFileReferences) {
			return;
		}
		File baseDir = referenceBaseDir();
		List<String> lines = lines(content);
		boolean[] insideFence = fenceMask(lines);
		for (int i = 0; i < lines.size(); i++) {
			collectLineReferences(lines.get(i), insideFence[i], baseDir, violations);
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

	/**
	 * Marks every line that belongs to a fenced code block, including the opening
	 * and closing {@code ```} delimiters, so heading detection and body detection
	 * agree on what is code and what is document structure.
	 */
	private boolean[] fenceMask(List<String> lines) {
		boolean[] mask = new boolean[lines.size()];
		boolean insideCodeFence = false;
		for (int i = 0; i < lines.size(); i++) {
			boolean isFenceDelimiter = lines.get(i).strip().startsWith(CODE_FENCE);
			mask[i] = insideCodeFence || isFenceDelimiter;
			insideCodeFence = isFenceDelimiter != insideCodeFence;
		}
		return mask;
	}

	private Set<String> headings(List<String> lines, boolean[] insideFence) {
		Set<String> headings = new LinkedHashSet<>();
		for (int i = 0; i < lines.size(); i++) {
			String trimmed = lines.get(i).strip();
			if (!insideFence[i] && isHeading(trimmed)) {
				headings.add(trimmed);
			}
		}
		return headings;
	}

	private int headingIndex(List<String> lines, boolean[] insideFence, String section) {
		for (int i = 0; i < lines.size(); i++) {
			if (!insideFence[i] && lines.get(i).strip().equals(section)) {
				return i;
			}
		}
		return -1;
	}

	/**
	 * A section has a body when it is followed, before the next heading at its own
	 * level or shallower, by any prose, a code block, or a deeper sub-heading. A
	 * deeper heading is content that belongs to the section; a sibling or parent
	 * heading ends it.
	 */
	private boolean hasBody(List<String> lines, boolean[] insideFence, int headingIndex) {
		int sectionLevel = headingLevel(lines.get(headingIndex).strip());
		for (int i = headingIndex + 1; i < lines.size(); i++) {
			String line = lines.get(i).strip();
			if (insideFence[i]) {
				return true;
			}
			if (isHeading(line)) {
				return headingLevel(line) > sectionLevel;
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

	private int headingLevel(String heading) {
		int level = 0;
		while (level < heading.length() && heading.charAt(level) == HEADING_CHAR) {
			level++;
		}
		return level;
	}

	private List<String> lines(String content) {
		return content.lines().toList();
	}
}
