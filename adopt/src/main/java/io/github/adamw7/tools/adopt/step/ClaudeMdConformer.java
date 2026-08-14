package io.github.adamw7.tools.adopt.step;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import io.github.adamw7.tools.markdown.MarkdownDocument;
import io.github.adamw7.tools.markdown.MarkdownText;

/**
 * Reshapes the {@code CLAUDE.md} that {@link ClaudeInitStep} generated so it
 * satisfies the {@code claudeMdFormat} rule {@link EnforcerStep} wires into the
 * build. A generic {@code claude init} writes natural, project-specific headings
 * and no {@code AGENTS.md} reference, while the rule demands a fixed set of
 * whole-line headings plus that reference — so without this reshape the adoption
 * fails its own {@link VerifyStep}.
 *
 * <p>The reshape is deterministic and conservative: a near-miss heading is
 * <em>renamed</em> in place so its body survives, only a genuinely absent section
 * is appended, and a required section left empty gets a stub body because the rule
 * fails an empty section just as it fails a missing one. Code — fenced or indented
 * — and commented-out text are left alone, mirroring how the rule matches, and
 * reshaping an already-conforming document is a no-op.
 *
 * <p>Which sections it demands is the caller's to choose, because the guard being
 * wired in differs by build system — see
 * {@link BuildSystem#requiredClaudeMdSections()}. The title and the
 * {@code AGENTS.md} reference are settled either way, since the step writes a
 * companion {@code AGENTS.md} for every adopted repository.
 *
 * <p>How the document is <em>read</em> — which lines are code, which sit inside an
 * HTML comment, and what heading a line declares — is {@link MarkdownDocument}'s to
 * decide, the same reader the {@code claudeMdFormat} rule judges the result with.
 * Spelling those readings out here a second time made the two drift apart, and
 * every drift produced one shape of bug: this class acted on lines the rule holds
 * to be code, or appended a section the rule already reads as present, and the
 * adoption failed the very check it exists to satisfy — after pushing the file.
 */
public class ClaudeMdConformer {

	/*
	 * These mirror io.github.adamw7.tools.enforcer.doc.ClaudeMdFormatRule in the
	 * claude-code-enforcer module. They are duplicated rather than imported because
	 * the adopt module does not depend on the enforcer module — a pipeline that
	 * shipped an enforcer rule would force the maven-enforcer API on every consumer.
	 * The copy is not trusted to stay in step: ClaudeMdConformerContractTest runs the
	 * real rule over this class's output, so a rule that asks for one more section
	 * fails this module's build rather than some adopted repository's.
	 */
	static final String TITLE = "# CLAUDE.md";
	static final String AGENTS_REFERENCE = "AGENTS.md";

	/**
	 * The sections the {@code claudeMdFormat} rule demands, and so the sections to
	 * conform to <em>where that rule is the guard being wired in</em> — the Maven
	 * path, {@link MavenBuildSystem#requiredClaudeMdSections()}. They are Java and
	 * Maven sections because the rule is a Java project's rule; a build system whose
	 * guard asks only that the file exist and carry something requires none.
	 */
	static final List<String> REQUIRED_SECTIONS = List.of(
			"## Project",
			"## Java version",
			"## Maven",
			"## Principles for Java Development",
			"## Testing",
			"## Dependencies");

	static final String AGENTS_REFERENCE_LINE = "See [AGENTS.md](AGENTS.md) for the companion agent guide.";

	private static final String STUB_BODY = "See [AGENTS.md](AGENTS.md).";

	/** The blank lines the reshape may leave at the end, dropped so the document keeps a single one. */
	private static final Pattern TRAILING_BLANK_LINES = Pattern.compile("\\n\\s*+\\z");

	private final List<String> requiredSections;

	/** Conforms to the full {@code claudeMdFormat} contract. */
	public ClaudeMdConformer() {
		this(REQUIRED_SECTIONS);
	}

	/**
	 * Conforms only as far as the guard being wired in actually demands. Stamping the
	 * {@code claudeMdFormat} sections onto every adopted repository put Java and Maven
	 * headings, each stubbed with a pointer to {@code AGENTS.md}, into projects that
	 * are neither — where nothing then checked them, the Gradle and GitHub Actions
	 * guards asking only that the file exist and carry something.
	 *
	 * @param requiredSections the headings to canonicalize, append and give a body to,
	 *                         empty for a guard that demands no particular section. A
	 *                         heading named twice is kept once: each is settled against
	 *                         the document as it stood before the pass, so a repeated
	 *                         one would have had a second near-miss renamed to it.
	 */
	public ClaudeMdConformer(List<String> requiredSections) {
		this.requiredSections = List.copyOf(new LinkedHashSet<>(requiredSections));
	}

	/**
	 * @return {@code content} reshaped so the {@code claudeMdFormat} rule passes,
	 *         with a single trailing newline
	 */
	public String conform(String content) {
		List<String> lines = splitLines(content);
		closeUnterminatedBlocks(lines);
		ensureTitle(lines);
		canonicalizeHeadings(lines);
		ensureRequiredSections(lines);
		ensureAgentsReference(lines);
		return join(lines);
	}

	/**
	 * The lines are split here rather than left to {@link MarkdownDocument#parse},
	 * because {@link String#lines()} drops the empty line a document's trailing
	 * newline leaves and this reshape appends to the list it splits.
	 *
	 * <p>A leading byte-order mark is dropped, the same reading the rule takes.
	 * Keeping it left the mark in front of {@code # CLAUDE.md} — which
	 * {@link String#strip()} does not shorten — so the reshape concluded the title was
	 * missing and prepended a second one, which the rule accepted either way.
	 */
	private List<String> splitLines(String content) {
		String normalized = LineTerminators.normalized(MarkdownText.stripByteOrderMark(content));
		return new ArrayList<>(List.of(normalized.split("\n", -1)));
	}

	/**
	 * Closes a fence and an HTML comment the document opened and never closed, so
	 * everything appended below lands outside the block: a section appended inside an
	 * open comment is inert text the rule does not see. A document whose fences and
	 * comments balance is left untouched, keeping the reshape idempotent.
	 *
	 * <p>The fence is closed first, because a comment inside a fenced code block is
	 * sample text rather than a comment — the same precedence the rule reads them
	 * with — and the document is read afresh afterwards so the added line counts.
	 */
	private void closeUnterminatedBlocks(List<String> lines) {
		MarkdownDocument.of(lines).openFenceTerminator().ifPresent(lines::add);
		MarkdownDocument.of(lines).openCommentTerminator().ifPresent(lines::add);
	}

	/**
	 * Puts the title on the document's first line, which is where the rule reads it.
	 * A title the document already carries lower down is <em>moved</em> rather than
	 * left where it is, since prepending a second one leaves two {@code # CLAUDE.md}
	 * headings — which the rule accepts, so nothing downstream would report the
	 * duplicate, and the reshape being idempotent means a re-adoption never clears it.
	 *
	 * <p>The titles are removed latest first, so removing one cannot shift the index
	 * of the next. Only structural lines are offered: a {@code # CLAUDE.md} inside a
	 * code sample is the sample's, not the document's.
	 */
	private void ensureTitle(List<String> lines) {
		MarkdownDocument document = MarkdownDocument.of(lines);
		if (TITLE.equals(document.firstNonBlankLine())) {
			return;
		}
		document.headingIndices(TITLE).boxed().toList().reversed().forEach(index -> removeHeading(lines, index));
		lines.addAll(0, List.of(TITLE, ""));
	}

	/**
	 * Removes the heading, and the blank line below it only when the line above was
	 * blank too — otherwise the paragraph that followed the heading would be pulled up
	 * against the one that preceded it and the two would read as a single paragraph.
	 */
	private static void removeHeading(List<String> lines, int index) {
		lines.remove(index);
		if (isBlankAt(lines, index) && precededByBlank(lines, index)) {
			lines.remove(index);
		}
	}

	private static boolean precededByBlank(List<String> lines, int index) {
		return index == 0 || lines.get(index - 1).isBlank();
	}

	/**
	 * Renaming a heading in place leaves the line count untouched and cannot turn a
	 * structural line into code, so one reading of the document serves every required
	 * section here — unlike {@link #ensureSection}, which inserts lines and has to
	 * read the document afresh each time.
	 */
	private void canonicalizeHeadings(List<String> lines) {
		MarkdownDocument document = MarkdownDocument.of(lines);
		Set<Integer> claimed = reservedHeadings(document);
		requiredSections.forEach(required -> canonicalize(lines, document, required, claimed));
	}

	/**
	 * The indices of lines that already are a required heading exactly, reserved so
	 * a near-match search never renames a heading that is already serving another
	 * required section.
	 */
	private Set<Integer> reservedHeadings(MarkdownDocument document) {
		return document
				.structuralLines(line -> MarkdownDocument.headingOf(line).filter(requiredSections::contains).isPresent())
				.boxed()
				.collect(Collectors.toCollection(LinkedHashSet::new));
	}

	private void canonicalize(List<String> lines, MarkdownDocument document, String required, Set<Integer> claimed) {
		if (document.headingIndex(required) >= 0) {
			return;
		}
		int index = firstNearMatch(document, required, claimed);
		if (index >= 0) {
			lines.set(index, required);
			claimed.add(index);
		}
	}

	private int firstNearMatch(MarkdownDocument document, String required, Set<Integer> claimed) {
		return document.structuralLines(line -> isNearMatch(line, required))
				.filter(index -> !claimed.contains(index))
				.findFirst()
				.orElse(-1);
	}

	/**
	 * A near-miss is the required heading in a different case, or followed by a
	 * space and extra words — the two shapes {@code claude init} produces
	 * ({@code ## Project purpose} for {@code ## Project}). The trailing space keeps
	 * {@code ## Maven} from matching an unrelated {@code ## Mavenish} heading. The
	 * line is compared as {@link MarkdownDocument#headingOf} writes it, so which
	 * spelling a near-miss was typed in does not decide whether it is one.
	 */
	private static boolean isNearMatch(String line, String required) {
		String wanted = required.toLowerCase(Locale.ROOT);
		return MarkdownDocument.headingOf(line)
				.map(heading -> heading.toLowerCase(Locale.ROOT))
				.filter(actual -> actual.equals(wanted) || actual.startsWith(wanted + " "))
				.isPresent();
	}

	/**
	 * Gives every required section both a heading and a body: an absent section is
	 * appended with a stub, and a heading the document left bare — typically a
	 * near-miss renamed in place above — has one inserted beneath it. The rule fails
	 * an empty section just as it fails a missing one, so both are settled here.
	 */
	private void ensureRequiredSections(List<String> lines) {
		requiredSections.forEach(required -> ensureSection(lines, required));
	}

	/**
	 * The document is read afresh per section because appending a section or
	 * inserting a stub shifts the lines the next one is found at, and the sections
	 * are few enough for that to stay cheap. Missing and empty are two answers to the
	 * one heading lookup, so it is made once.
	 */
	private void ensureSection(List<String> lines, String required) {
		MarkdownDocument document = MarkdownDocument.of(lines);
		int index = document.headingIndex(required);
		if (index < 0) {
			lines.addAll(List.of("", required, "", STUB_BODY));
		} else if (!document.hasBodyAt(index)) {
			lines.addAll(index + 1, List.of("", STUB_BODY));
		}
	}

	/**
	 * The reference is separated from what follows it only when that line is not
	 * already blank, since a document conformed to no required sections keeps the body
	 * {@code claude init} wrote, blank line and all.
	 *
	 * <p>An existing mention only counts where the rule counts one: its
	 * {@code containsInProse} reads the lines that carry document structure, so a
	 * mention inside a code sample or an HTML comment satisfies it no more than a
	 * commented-out heading satisfies a required section. Asking the looser question
	 * let a document whose only {@code AGENTS.md} sat in a comment keep a reference it
	 * never had. A document with no title line at all takes the reference at the top.
	 */
	private void ensureAgentsReference(List<String> lines) {
		MarkdownDocument document = MarkdownDocument.of(lines);
		if (document.containsInProse(AGENTS_REFERENCE)) {
			return;
		}
		int index = Math.max(document.headingIndex(TITLE), 0) + 1;
		List<String> reference = new ArrayList<>(List.of("", AGENTS_REFERENCE_LINE));
		if (!isBlankAt(lines, index)) {
			reference.add("");
		}
		lines.addAll(index, reference);
	}

	private static boolean isBlankAt(List<String> lines, int index) {
		return index < lines.size() && lines.get(index).isBlank();
	}

	/** Joins the lines under a single trailing newline, dropping any blank lines the reshape left at the end. */
	private String join(List<String> lines) {
		return TRAILING_BLANK_LINES.matcher(String.join("\n", lines)).replaceAll("") + "\n";
	}
}
