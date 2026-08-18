package io.github.adamw7.tools.enforcer.rule;

import java.io.File;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.maven.enforcer.rule.api.EnforcerRuleException;

import io.github.adamw7.tools.markdown.LineTerminators;
import io.github.adamw7.tools.markdown.MarkdownConformer;
import io.github.adamw7.tools.markdown.MarkdownContract;
import io.github.adamw7.tools.markdown.MarkdownDocument;
import io.github.adamw7.tools.markdown.MarkdownText;

/**
 * Base for enforcer rules that validate a Markdown document follows an expected
 * structure: it must exist, be non-empty, start with a required title heading (a
 * leading UTF-8 BOM is tolerated), and contain every required section heading as a
 * real, non-empty heading.
 * <p>
 * Headings are matched outside code, so a heading mentioned in a sample — fenced or
 * indented — or in prose does not satisfy a requirement, and a partial match such as
 * {@code # CLAUDE.md-extended} does not satisfy {@code # CLAUDE.md}. They are
 * matched by the text they carry rather than by the line they were typed on, so a
 * heading balanced with a closing {@code #} run satisfies the requirement its plain
 * spelling does — the title included. All structural problems are collected and
 * reported together.
 * <p>
 * Several optional checks, each disabled by default, can be switched on from the
 * rule configuration: {@code forbiddenTokens} that must not appear outside code,
 * {@code enforceSectionOrder}, a {@code maxLineLength} cap, and
 * {@code validateFileReferences} to confirm that links to local files resolve on
 * disk. The title and required sections default to the subclass-provided values
 * but can be overridden, so the rule is reusable across projects without a
 * recompile.
 * <p>
 * With {@code <autoFix>true</autoFix>} the structural part of that contract is
 * <em>repaired</em> rather than merely reported: the document is reshaped by
 * {@link MarkdownConformer} — the same reshape the adoption pipeline runs, against
 * the same {@link MarkdownDocument} reader this rule judges the result with — and
 * the checks then run over the repaired document. A missing title, a heading that
 * is a near miss for a required one, an absent or empty section, and a missing
 * companion-document reference are all mechanical edits with one correct answer,
 * and reporting them left that answer to be typed out by hand on every project
 * that adopts the rule.
 * <p>
 * Only that structural part is repaired. A forbidden token, an over-long line and
 * a broken file reference are still reported, because each of those has no one
 * correct repair — what a link ought to point at is the author's to say.
 */
public abstract class MarkdownFormatRule extends ClaudeCodeEnforcerRule {

	/**
	 * A Markdown link, whose destination may carry one level of balanced parentheses
	 * — {@code [img](assets/logo(1).png)} — as Markdown allows. Stopping at the first
	 * closing parenthesis truncated such a target and reported the half of it that
	 * naturally does not exist as a missing file.
	 */
	private static final Pattern MARKDOWN_LINK = Pattern.compile("\\[[^\\]]*\\]\\(((?:[^()]|\\([^()]*\\))+)\\)");
	private static final Pattern EXTERNAL_REFERENCE = Pattern.compile("^[a-zA-Z][a-zA-Z0-9+.-]*:.*");

	private final String documentName;
	private final List<String> defaultRequiredSections;

	/** Optional override for the title heading. Falls back to the subclass default. */
	private String titleHeading;

	/** Optional override for the required sections. Falls back to the subclass default. */
	private List<String> requiredSections;

	/** Optional tokens that must not appear outside code. */
	private List<String> forbiddenTokens;

	/** When true, the required sections must appear in the configured order. */
	private boolean enforceSectionOrder;

	/** Maximum allowed line length outside code. Zero (default) disables the check. */
	private int maxLineLength;

	/** When true, Markdown links to local files must resolve to an existing file. */
	private boolean validateFileReferences;

	/** Base directory for resolving relative file references. Defaults to the document's directory. */
	private File referenceBaseDir;

	/** When true, the document's structure is repaired in place before it is checked. */
	private boolean autoFix;

	/**
	 * @param documentName            human-readable file name used in messages, e.g.
	 *                                {@code CLAUDE.md}
	 * @param defaultRequiredSections the section headings the document must contain
	 *                                unless the configuration overrides them
	 */
	protected MarkdownFormatRule(String documentName, List<String> defaultRequiredSections) {
		this.documentName = documentName;
		this.defaultRequiredSections = defaultRequiredSections;
	}

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
	protected final String documentName() {
		return documentName;
	}

	/**
	 * The default title heading the document must start with. A document is titled
	 * after itself, e.g. {@code # CLAUDE.md}, so the file name is the default; a
	 * document titled otherwise overrides this.
	 */
	protected String defaultTitleHeading() {
		return "# " + documentName;
	}

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
		return Objects.requireNonNullElseGet(titleHeading, this::defaultTitleHeading);
	}

	final List<String> requiredSections() {
		return Objects.requireNonNullElse(requiredSections, defaultRequiredSections);
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

	public void setAutoFix(boolean autoFix) {
		this.autoFix = autoFix;
	}

	/**
	 * The companion document the checked document must mention in prose, empty when
	 * it must mention none. The base class asks so that a repair can insert a missing
	 * reference; the check for it belongs to whichever subclass makes it.
	 */
	protected String requiredReference() {
		return "";
	}

	/**
	 * The shape a repair reshapes the document into: exactly what this rule goes on to
	 * check it for, read from the same configured parameters. Deriving one from the
	 * other is what stops a repair satisfying a contract the rule no longer makes.
	 */
	private MarkdownContract contract() {
		return MarkdownContract.titled(titleHeading())
				.requiring(requiredSections())
				.referencing(requiredReference());
	}

	/**
	 * Reading and parsing both go through {@link DocumentCache}, so the five rules
	 * that each check something about {@code CLAUDE.md} parse it once between them.
	 * They stay independent either way: what is shared is the document, not any
	 * rule's reading of it.
	 */
	private MarkdownDocument readDocument() throws EnforcerRuleException {
		File file = documentFile();
		requireDocument(file, documentName());
		String content = repaired(file, requireContent(file, documentName()));
		return DocumentCache.markdown(file, content);
	}

	/**
	 * Reshapes the document and writes it back when {@code autoFix} is on and the
	 * reshape actually changed something, answering the content the checks then run
	 * over. A reshape that changed nothing is not written, so a conforming document
	 * keeps its modification time and a build that repairs nothing touches no file.
	 *
	 * <p>The line terminators the file already used are put back, so repairing a CRLF
	 * document does not rewrite every line of it as a side effect.
	 */
	private String repaired(File file, String content) throws EnforcerRuleException {
		if (!autoFix) {
			return content;
		}
		String conformed = LineTerminators.matching(new MarkdownConformer(contract()).conform(content), content);
		if (conformed.equals(content)) {
			return content;
		}
		MarkdownText.write(file, conformed, documentName());
		log().info("Auto-fixed the structure of " + documentName() + " in " + file);
		return conformed;
	}

	/**
	 * The title is matched the way every other heading here is: by the text it
	 * carries rather than by the line it was typed on. Comparing the raw first line
	 * instead made the rule contradict itself — a {@code # CLAUDE.md ##} balanced
	 * with a closing run, or one separated by two spaces or a tab, satisfies every
	 * required-section lookup in this class and was still reported as a document that
	 * "must start with the '# CLAUDE.md' title heading", which is exactly what it
	 * does start with.
	 * <p>
	 * A configured title that is not a heading at all is compared verbatim, since
	 * there is no canonical form to read it into.
	 */
	private void collectTitleViolation(MarkdownDocument document, List<String> violations) {
		if (!declaresTitle(document.firstNonBlankLine())) {
			violations.add(documentName() + " must start with the '" + titleHeading() + "' title heading");
		}
	}

	private boolean declaresTitle(String firstLine) {
		String expected = titleHeading();
		return MarkdownDocument.headingOf(expected)
				.map(canonical -> MarkdownDocument.headingOf(firstLine).filter(canonical::equals).isPresent())
				.orElseGet(() -> firstLine.equals(expected));
	}

	/**
	 * Missing and empty are two answers to one lookup, so the heading is located
	 * once per section and the body question asked of the index it found. Asking
	 * {@code hasHeading} and then {@code hasBody} walked the document twice per
	 * section to answer a question the first walk had already settled.
	 */
	private void collectSectionViolations(MarkdownDocument document, List<String> violations) {
		for (String section : requiredSections()) {
			int index = document.headingIndex(section);
			if (index < 0) {
				violations.add(documentName() + " is missing required section heading: " + section);
			} else if (!document.hasBodyAt(index)) {
				violations.add(documentName() + " has an empty section: " + section);
			}
		}
	}

	/**
	 * When ordering is enforced, the required sections that are present must appear
	 * in the same relative order as configured. Absent sections are already
	 * reported by the section check, so only the present ones are compared here.
	 * <p>
	 * A section named twice in the configuration counts once, because that is how
	 * {@link MarkdownDocument#headingsInOrder} reports the document's side of the
	 * comparison: the document was expected to carry the repeated heading twice and
	 * answered with the one heading it has, so a correctly ordered document was
	 * reported as out of order and no edit to it could settle the complaint.
	 */
	private void collectOrderViolations(MarkdownDocument document, List<String> violations) {
		if (!enforceSectionOrder) {
			return;
		}
		Set<String> headings = document.headings();
		List<String> expected = requiredSections().stream().distinct().filter(headings::contains).toList();
		List<String> actual = document.headingsInOrder(requiredSections());
		if (!actual.equals(expected)) {
			violations.add(documentName() + " sections are out of order; expected " + expected + " but found " + actual);
		}
	}

	private void collectForbiddenTokenViolations(MarkdownDocument document, List<String> violations) {
		if (forbiddenTokens == null) {
			return;
		}
		for (String token : forbiddenTokens) {
			if (document.containsUnquoted(token)) {
				violations.add(documentName() + " must not contain forbidden token: " + token);
			}
		}
	}

	private void collectLineLengthViolations(MarkdownDocument document, List<String> violations) {
		if (maxLineLength <= 0) {
			return;
		}
		for (int i = 0; i < document.lineCount(); i++) {
			String line = document.line(i);
			if (!document.isInsideCode(i) && line.length() > maxLineLength) {
				violations.add(documentName() + " line " + (i + 1) + " exceeds " + maxLineLength
						+ " characters (" + line.length() + ")");
			}
		}
	}

	private void collectFileReferenceViolations(MarkdownDocument document, List<String> violations) {
		if (!validateFileReferences) {
			return;
		}
		File baseDir = referenceBaseDir();
		for (int i = 0; i < document.lineCount(); i++) {
			collectLineReferences(document, i, baseDir, violations);
		}
	}

	/**
	 * Links are read outside code, HTML comments and inline code spans alike, so a
	 * sample link written as {@code `[label](example.md)`} is documentation rather
	 * than a reference this rule must resolve on disk, and a link an author
	 * commented out is one the document no longer makes.
	 */
	private void collectLineReferences(MarkdownDocument document, int index, File baseDir, List<String> violations) {
		if (document.isInsideCode(index) || document.isInsideComment(index)) {
			return;
		}
		Matcher matcher = MARKDOWN_LINK.matcher(MarkdownText.withoutCodeSpans(document.line(index)));
		while (matcher.find()) {
			addReferenceViolation(localReferencePath(linkDestination(matcher.group(1))), baseDir, violations);
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

	private void addReferenceViolation(String localPath, File baseDir, List<String> violations) {
		if (localPath != null && !resolves(localPath, baseDir)) {
			violations.add(documentName() + " references a missing file: " + localPath);
		}
	}

	/**
	 * True when the link lands on a file, read both as written and with its
	 * percent-escapes decoded. A Markdown destination escapes the characters a URL
	 * cannot carry raw — a space becomes {@code %20} — so a link to a real
	 * {@code my doc.md} is written {@code my%20doc.md}, and matching only the literal
	 * spelling reported every such file as missing. Both readings are accepted rather
	 * than the decoded one alone, so a file whose name genuinely contains a percent
	 * sign still resolves.
	 */
	private boolean resolves(String localPath, File baseDir) {
		return new File(baseDir, localPath).exists() || new File(baseDir, decoded(localPath)).exists();
	}

	/** The path with its percent-escapes decoded, or unchanged when they are malformed. */
	private String decoded(String localPath) {
		try {
			return URLDecoder.decode(localPath, StandardCharsets.UTF_8);
		} catch (IllegalArgumentException e) {
			return localPath;
		}
	}

	/** The on-disk path a link points to, or null when the link is external or an anchor. */
	private String localReferencePath(String target) {
		if (target.isEmpty() || target.startsWith("#") || EXTERNAL_REFERENCE.matcher(target).matches()) {
			return null;
		}
		String path = target.split("[#?]", 2)[0];
		return path.isEmpty() ? null : path;
	}

	private File referenceBaseDir() {
		if (referenceBaseDir != null) {
			return referenceBaseDir;
		}
		File parent = documentFile().getAbsoluteFile().getParentFile();
		return parent != null ? parent : new File(".");
	}
}
