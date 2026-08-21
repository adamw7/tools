package io.github.adamw7.tools.markdown;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Reshapes a Markdown document until it satisfies a {@link MarkdownContract}: it
 * opens with the required title, mentions the companion document the contract
 * names, and carries every required section heading with a body under each.
 *
 * <p>It exists because the two things that produce such a document — a generator
 * writing natural, project-specific headings, and an author editing by hand —
 * both produce near misses, and a check that only reports them leaves the same
 * mechanical edits to be made by hand every time. The reshape is deterministic and
 * conservative: a near-miss heading is <em>renamed</em> in place so its body
 * survives, only a genuinely absent section is appended, and a required section
 * left empty gets a stub body because a check fails an empty section just as it
 * fails a missing one. Code — fenced or indented — and commented-out text are left
 * alone, mirroring how such a check matches, and reshaping an already-conforming
 * document is a no-op.
 *
 * <p>How the document is <em>read</em> — which lines are code, which sit inside an
 * HTML comment, and what heading a line declares — is {@link MarkdownDocument}'s
 * to decide, the same reader the checks judge the result with, for the reasons
 * that class gives.
 */
public class MarkdownConformer {

	/** The blank lines the reshape may leave at the end, dropped so the document keeps a single one. */
	private static final Pattern TRAILING_BLANK_LINES = Pattern.compile("\\n\\s*+\\z");

	/**
	 * How many times {@link #conform} may run the pass before it stops waiting for the
	 * document to settle. Two is the ordinary cost — one pass that reshapes and one
	 * that confirms the reshape left nothing behind — and a third is spent only where
	 * a pass's own edit reclassified a line an earlier part of that same pass had read.
	 * The bound is what stops a document nothing settles from looping; such a document
	 * comes back as the last pass left it, for the caller's own check to refuse.
	 */
	private static final int MAX_PASSES = 4;

	private final MarkdownContract contract;

	/**
	 * @param contract the shape to reshape a document into — the same one the check
	 *                 that judges the result is configured with
	 */
	public MarkdownConformer(MarkdownContract contract) {
		this.contract = contract;
	}

	/**
	 * Reshapes until the document stops changing, which is the only way one pass can
	 * be trusted: an edit made part-way through a pass changes how the lines below it
	 * are read, so a question the pass had already answered can stop being true before
	 * the pass ends. Every edit here is one of those — an inserted reference or stub
	 * puts a blank line or a margin line where the reading of the lines below turns on
	 * one, and a renamed heading moves to the margin the same way. Each left the
	 * reshape reporting work it had done on a document that no longer existed, and the
	 * caller's own check failing on the document the reshape had just produced;
	 * {@link #ensureRequiredSections} works one such case through.
	 *
	 * <p>What makes repeating the pass an answer rather than a retry is that a pass
	 * which changes nothing is exactly a document the check accepts: the pass leaves a
	 * title alone only where the check finds one, leaves the reference alone only
	 * where {@code containsInProse} finds one, and leaves a section alone only where
	 * the check's own lookup finds it carrying a body. So the loop ends on the very
	 * condition the check makes, and a document that already conforms settles on the
	 * first pass — which is what keeps the reshape a no-op when it is run again.
	 *
	 * @return {@code content} reshaped to satisfy the contract, with a single
	 *         trailing newline
	 */
	public String conform(String content) {
		String conformed = reshape(content);
		for (int pass = 1; pass < MAX_PASSES; pass++) {
			String settled = reshape(conformed);
			if (settled.equals(conformed)) {
				return conformed;
			}
			conformed = settled;
		}
		return conformed;
	}

	/**
	 * One pass, with the two insertions that go above the document's body — the title
	 * and the reference — made before a section is looked for. The loop above is what
	 * makes the result sound whatever this order is, but settling those first is what
	 * lets the ordinary generated document reshape in one pass rather than two, and a
	 * pass that finds work left over appends it at the end of the file.
	 */
	private String reshape(String content) {
		List<String> lines = splitLines(content);
		closeUnterminatedBlocks(lines);
		ensureTitle(lines);
		ensureReference(lines);
		canonicalizeHeadings(lines);
		ensureRequiredSections(lines);
		return join(lines);
	}

	/**
	 * The lines are split here rather than left to {@link MarkdownDocument#parse},
	 * because {@link String#lines()} drops the empty line a document's trailing
	 * newline leaves and this reshape appends to the list it splits.
	 *
	 * <p>A leading byte-order mark is dropped, the same reading a check takes. Keeping
	 * it left the mark in front of the title — which {@link String#strip()} does not
	 * shorten — so the reshape concluded the title was missing and prepended a second
	 * one, which the check accepted either way.
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
	 * sample text rather than a comment — the same precedence a check reads them
	 * with — and the document is read afresh afterwards so the added line counts.
	 */
	private void closeUnterminatedBlocks(List<String> lines) {
		MarkdownDocument.of(lines).openFenceTerminator().ifPresent(lines::add);
		MarkdownDocument.of(lines).openCommentTerminator().ifPresent(lines::add);
	}

	/**
	 * Puts the title on the document's first line, which is where the rule reads it.
	 * A title the document already carries lower down is <em>moved</em> rather than
	 * left where it is, since prepending a second one leaves two title headings —
	 * which a check accepts, so nothing downstream would report the duplicate, and the
	 * reshape being idempotent means running it again never clears it.
	 *
	 * <p>A document that already opens with the title is not left alone either: it
	 * needs no title <em>added</em>, but a second one lower down is that same
	 * duplicate by another route, and returning on the first line alone left it there
	 * for good.
	 *
	 * <p>Only structural lines are offered: a {@code # CLAUDE.md} inside a code sample
	 * is the sample's, not the document's.
	 */
	private void ensureTitle(List<String> lines) {
		MarkdownDocument document = MarkdownDocument.of(lines);
		List<Integer> titles = document.headingIndices(contract.title()).boxed().toList();
		if (contract.title().equals(document.firstNonBlankLine())) {
			removeHeadings(lines, allButTheFirst(titles));
			return;
		}
		removeHeadings(lines, titles);
		lines.addAll(0, List.of(contract.title(), ""));
	}

	/**
	 * The titles a document opening with one has to lose: every one but the title it
	 * opens with, which is the one a check reads. An empty list is the document whose
	 * opening title is not a structural line at all — one indented into a code block,
	 * which a check accepts on the same reading and which is the sample's to keep.
	 */
	private static List<Integer> allButTheFirst(List<Integer> titles) {
		return titles.isEmpty() ? titles : titles.subList(1, titles.size());
	}

	/** Removes the headings latest first, so removing one cannot shift the index of the next. */
	private static void removeHeadings(List<String> lines, List<Integer> indices) {
		indices.reversed().forEach(index -> removeHeading(lines, index));
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
	 * section here — unlike {@link #stubBareSection}, which inserts lines and has to
	 * read the document afresh each time.
	 */
	private void canonicalizeHeadings(List<String> lines) {
		MarkdownDocument document = MarkdownDocument.of(lines);
		Set<Integer> claimed = reservedHeadings(document);
		contract.requiredSections().forEach(required -> canonicalize(lines, document, required, claimed));
	}

	/**
	 * The indices of lines that already are a required heading exactly, reserved so
	 * a near-match search never renames a heading that is already serving another
	 * required section.
	 */
	private Set<Integer> reservedHeadings(MarkdownDocument document) {
		return document
				.structuralLines(line -> MarkdownDocument.headingOf(line).filter(contract.requiredSections()::contains).isPresent())
				.boxed()
				.collect(Collectors.toCollection(LinkedHashSet::new));
	}

	private void canonicalize(List<String> lines, MarkdownDocument document, String required,
			Set<Integer> claimed) {
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
	 * A near-miss is the required heading in a different case, or followed by a space
	 * and extra words — the two shapes a generator produces ({@code ## Project
	 * purpose} for {@code ## Project}). The trailing space keeps
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
	 * Gives every required section both a heading and a body: a heading the document
	 * left bare — typically a near-miss renamed in place above — has a stub inserted
	 * beneath it, and a section the document does not carry at all is appended with
	 * one. A check fails an empty section just as it fails a missing one, so both are
	 * settled here.
	 *
	 * <p>The two are separate passes, with the document's blocks closed again between
	 * them, because inserting a stub is a mid-document edit and a mid-document edit
	 * can change what the lines below it are. A stub sits at the margin, which ends an
	 * open list — and that lowers the column at which the lines below quote code, so a
	 * fence delimiter indented inside that list stops being one. The terminator
	 * {@link #closeUnterminatedBlocks} had already appended for it then <em>opened</em>
	 * a block instead of closing one, and every section appended afterwards landed
	 * inside it, where a check reads it as code. Interleaving the two passes has the
	 * same flaw one step further on, since a stub inserted after a section was
	 * appended can bury that section the same way. Appending, by contrast, only ever
	 * adds below every line that has been read, so it can invalidate nothing.
	 */
	private void ensureRequiredSections(List<String> lines) {
		contract.requiredSections().forEach(required -> stubBareSection(lines, required));
		closeUnterminatedBlocks(lines);
		contract.requiredSections().forEach(required -> appendAbsentSection(lines, required));
	}

	/**
	 * The document is read afresh per section because inserting a stub shifts the
	 * lines the next one is found at, and the sections are few enough for that to stay
	 * cheap. Missing and empty are two answers to the one heading lookup, so it is
	 * made once: a section the document does not declare is left to
	 * {@link #appendAbsentSection}.
	 */
	private void stubBareSection(List<String> lines, String required) {
		MarkdownDocument document = MarkdownDocument.of(lines);
		int index = document.headingIndex(required);
		if (index >= 0 && !document.hasBodyAt(index)) {
			lines.addAll(index + 1, List.of("", contract.stubBody()));
		}
	}

	/** Appends the section and its stub when the document declares the heading nowhere. */
	private void appendAbsentSection(List<String> lines, String required) {
		if (MarkdownDocument.of(lines).headingIndex(required) < 0) {
			lines.addAll(List.of("", required, "", contract.stubBody()));
		}
	}

	/**
	 * The reference is separated from what follows it only when that line is not
	 * already blank, since a document conformed to no required sections keeps the body
	 * {@code claude init} wrote, blank line and all.
	 *
	 * <p>An existing mention only counts where the rule counts one: its
	 * {@code containsInProse} reads the lines that carry document structure, so a
	 * mention inside a code sample or an HTML comment does not satisfy it. Asking the
	 * looser question let a document whose only {@code AGENTS.md} sat in a comment
	 * keep a reference it never had. A document with no title line at all takes the
	 * reference at the top.
	 */
	private void ensureReference(List<String> lines) {
		MarkdownDocument document = MarkdownDocument.of(lines);
		if (document.containsInProse(contract.requiredReference())) {
			return;
		}
		int index = Math.max(document.headingIndex(contract.title()), 0) + 1;
		lines.addAll(index, isBlankAt(lines, index)
				? List.of("", contract.referenceLine())
				: List.of("", contract.referenceLine(), ""));
	}

	private static boolean isBlankAt(List<String> lines, int index) {
		return index < lines.size() && lines.get(index).isBlank();
	}

	/** Joins the lines under a single trailing newline, dropping any blank lines the reshape left at the end. */
	private String join(List<String> lines) {
		return TRAILING_BLANK_LINES.matcher(String.join("\n", lines)).replaceAll("") + "\n";
	}
}
