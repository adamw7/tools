package io.github.adamw7.tools.adopt.step;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;

class ClaudeMdConformerTest {

	private final ClaudeMdConformer conformer = new ClaudeMdConformer();

	private List<String> headings(String content) {
		return content.lines().map(String::strip).filter(line -> line.startsWith("#")).toList();
	}

	@Test
	void canonicalisesNearMissHeadingsInPlacePreservingBody() {
		String generated = """
				# CLAUDE.md

				## Project purpose

				A security playground.

				## Java version

				Java 25.

				## Maven module structure

				Root pom is packaging=pom.

				## Principles for Java development

				SOLID.

				## Testing

				JUnit 5.

				## Dependencies

				Existing only.
				""";
		String conformed = conformer.conform(generated);
		List<String> headings = headings(conformed);
		assertTrue(headings.containsAll(ClaudeMdConformer.REQUIRED_SECTIONS), headings.toString());
		assertFalse(conformed.contains("## Project purpose"), "the near-miss heading should be renamed, not duplicated");
		assertFalse(conformed.contains("## Maven module structure"), "the near-miss heading should be renamed");
		assertTrue(conformed.contains("A security playground."), "the renamed section keeps its body");
		assertTrue(conformed.contains("Root pom is packaging=pom."), "the renamed section keeps its body");
	}

	/**
	 * A heading is only half of what the rule checks: it fails an empty section
	 * just as it fails a missing one, so a near-miss renamed over a bare section
	 * has to come out with a body or the adoption fails its own verification.
	 */
	@Test
	void givesARenamedNearMissWithNoContentAStubBody() {
		String generated = """
				# CLAUDE.md

				## Project purpose

				## Build commands

				Run `mvn install`.
				""";
		String conformed = conformer.conform(generated);
		assertTrue(conformed.contains("## Project\n\nSee [AGENTS.md](AGENTS.md)."),
				"the emptied section must be given a body:\n" + conformed);
		ClaudeMdConformer.REQUIRED_SECTIONS.forEach(section -> assertTrue(hasBody(conformed, section),
				section + " must have a body:\n" + conformed));
	}

	@Test
	void keepsTheBodyOfASectionThatAlreadyHasOne() {
		String generated = "# CLAUDE.md\n\n" + requiredSectionsBody();
		String conformed = conformer.conform(generated);
		assertEquals(ClaudeMdConformer.REQUIRED_SECTIONS.size(), conformed.split("Content\\.", -1).length - 1,
				"every original body must survive exactly once:\n" + conformed);
		assertFalse(conformed.contains("Content.\n\nSee [AGENTS.md](AGENTS.md)."),
				"a section that already has a body must not be given a stub");
	}

	/**
	 * Mirrors the enforcer rule's own check: the section must carry something other
	 * than blank lines before the next heading at its level or shallower.
	 */
	private boolean hasBody(String content, String section) {
		List<String> lines = content.lines().map(String::strip).toList();
		int start = lines.indexOf(section);
		return start >= 0 && lines.stream().skip(start + 1L).dropWhile(String::isEmpty).findFirst()
				.filter(line -> !line.startsWith("## ")).isPresent();
	}

	@Test
	void insertsAgentsReferenceWhenAbsent() {
		String generated = "# CLAUDE.md\n\n" + requiredSectionsBody();
		assertFalse(generated.contains(ClaudeMdConformer.AGENTS_REFERENCE));
		String conformed = conformer.conform(generated);
		assertTrue(conformed.contains(ClaudeMdConformer.AGENTS_REFERENCE), "an AGENTS.md reference must be added");
		assertEquals(ClaudeMdConformer.TITLE, conformed.lines().findFirst().orElseThrow(),
				"the title must stay the first line");
	}

	@Test
	void appendsAStubForAGenuinelyMissingSection() {
		String generated = """
				# CLAUDE.md

				See AGENTS.md.

				## Project

				A repo.

				## Java version

				Java 25.

				## Maven

				Maven.

				## Principles for Java Development

				SOLID.

				## Dependencies

				Existing only.
				""";
		assertFalse(generated.contains("## Testing"));
		String conformed = conformer.conform(generated);
		assertTrue(headings(conformed).contains("## Testing"), "the missing section is scaffolded");
		assertTrue(headings(conformed).containsAll(ClaudeMdConformer.REQUIRED_SECTIONS));
	}

	@Test
	void addsTheTitleWhenTheDocumentDoesNotStartWithIt() {
		String generated = "## Project\n\nA repo.\n";
		String conformed = conformer.conform(generated);
		assertEquals(ClaudeMdConformer.TITLE, conformed.lines().findFirst().orElseThrow());
	}

	/**
	 * The rule reads the title on the first line and nowhere else, so a document
	 * carrying it lower down still needs one put in front. Prepending it left the file
	 * with two {@code # CLAUDE.md} headings, which the rule accepts — it never looks
	 * past the first line — so the duplicate was committed to the adopted repository
	 * with nothing downstream to report it, and the reshape being idempotent meant a
	 * re-adoption did not clear it either.
	 */
	@Test
	void movesAMisplacedTitleUpRatherThanAddingASecondOne() {
		String generated = "A preamble the generator wrote.\n\n# CLAUDE.md\n\nBody here.\n";
		String conformed = conformer.conform(generated);
		assertEquals(ClaudeMdConformer.TITLE, conformed.lines().findFirst().orElseThrow());
		assertEquals(1, conformed.lines().filter(ClaudeMdConformer.TITLE::equals).count(),
				"the title must be moved, not duplicated:\n" + conformed);
		assertTrue(conformed.contains("A preamble the generator wrote."), "the preamble survives");
		assertTrue(conformed.contains("Body here."), "the body survives");
		assertEquals(conformed, conformer.conform(conformed), "the move must be idempotent");
	}

	/**
	 * Only the document's own title moves. One inside a code sample belongs to the
	 * sample — the rule reads it as code — so removing it would rewrite the sample.
	 */
	@Test
	void leavesATitleInsideACodeFenceWhereItIs() {
		String generated = "A preamble.\n\n```markdown\n# CLAUDE.md\n```\n";
		String conformed = conformer.conform(generated);
		assertEquals(ClaudeMdConformer.TITLE, conformed.lines().findFirst().orElseThrow());
		assertEquals(2, conformed.lines().filter(ClaudeMdConformer.TITLE::equals).count(),
				"the sample keeps its line and the document gains a title of its own:\n" + conformed);
		assertTrue(conformed.contains("```markdown"), "the fence survives");
	}

	/**
	 * The blank line below a moved title goes with it only when one above it is left
	 * behind; otherwise the paragraph that followed the title would be pulled up
	 * against the one before it and the two would read as a single paragraph.
	 */
	@Test
	void doesNotRunTwoParagraphsTogetherWhenMovingTheTitle() {
		String conformed = conformer.conform("A preamble.\n# CLAUDE.md\n\nBody here.\n");
		assertTrue(conformed.contains("A preamble.\n\nBody here."),
				"the two paragraphs must stay separate:\n" + conformed);
	}

	/**
	 * The rule strips a leading byte-order mark before it reads the document, and
	 * {@link String#strip()} does not — the mark is not whitespace. So a title the rule
	 * was perfectly happy with read as absent here, and the reshape prepended a second
	 * one, leaving the adoption's first commit carrying the title twice with nothing
	 * downstream to report it.
	 */
	@Test
	void recognisesATitleBehindAByteOrderMark() {
		String conforming = conforming();
		String conformed = conformer.conform("\uFEFF" + conforming);
		assertEquals(1, conformed.lines().filter(ClaudeMdConformer.TITLE::equals).count(),
				"the title must be recognised, not added a second time:\n" + conformed);
		assertEquals(conforming, conformed, "nothing but the mark itself may change");
	}

	@Test
	void ignoresHeadingsInsideCodeFencesWhenCanonicalising() {
		String generated = """
				# CLAUDE.md

				See AGENTS.md.

				```markdown
				## Project purpose
				```

				## Project

				A repo.

				## Java version

				Java 25.

				## Maven

				Maven.

				## Principles for Java Development

				SOLID.

				## Testing

				JUnit 5.

				## Dependencies

				Existing only.
				""";
		String conformed = conformer.conform(generated);
		assertTrue(conformed.contains("## Project purpose"), "the fenced heading must be left untouched");
		assertTrue(headings(conformed).contains("## Project"));
	}

	@Test
	void leavesAnAlreadyConformingDocumentUnchanged() {
		String conforming = conforming();
		String conformed = conformer.conform(conforming);
		assertEquals(conforming, conformed, "a conforming document must be a no-op");
	}

	/** A document the reshape has nothing to do to, for the tests that are about what it leaves alone. */
	private String conforming() {
		return ("# CLAUDE.md\n\n" + ClaudeMdConformer.AGENTS_REFERENCE_LINE + "\n\n"
				+ requiredSectionsBody()).stripTrailing() + "\n";
	}

	@Test
	void normalisationIsIdempotent() {
		String generated = """
				# CLAUDE.md

				## Project purpose

				A repo.

				## Java version

				Java 25.

				## Maven module structure

				Maven.

				## Principles for Java development

				SOLID.

				## Testing

				JUnit 5.

				## Dependencies

				Existing only.
				""";
		String once = conformer.conform(generated);
		String twice = conformer.conform(once);
		assertEquals(once, twice, "re-running the conformer must not churn the file");
	}

	@Test
	void countsAFencedCodeBlockAsASectionBody() {
		String generated = """
				# CLAUDE.md

				See AGENTS.md.

				## Project

				```bash
				mvn install
				```

				## Java version

				Java 25.
				""";
		String conformed = conformer.conform(generated);
		assertFalse(conformed.contains("## Project\n\nSee [AGENTS.md](AGENTS.md)."),
				"a section whose body is a code block must not be given a stub:\n" + conformed);
	}

	@Test
	void countsADeeperSubHeadingAsASectionBody() {
		String generated = """
				# CLAUDE.md

				See AGENTS.md.

				## Testing

				### Unit tests

				JUnit 5.
				""";
		String conformed = conformer.conform(generated);
		assertFalse(conformed.contains("## Testing\n\nSee [AGENTS.md](AGENTS.md)."),
				"a section carrying a sub-heading must not be given a stub:\n" + conformed);
	}

	@Test
	void leavesHeadingsInsideTildeFencesAlone() {
		String generated = """
				# CLAUDE.md

				See AGENTS.md.

				~~~markdown
				## Project purpose
				~~~

				## Project

				A repo.
				""";
		String conformed = conformer.conform(generated);
		assertTrue(conformed.contains("## Project purpose"), "the tilde-fenced heading must be left untouched");
		assertTrue(headings(conformed).contains("## Project"));
	}

	/**
	 * Markdown quotes code by indenting it four columns as well as by fencing it, and
	 * the rule reads both the same way. Reading only fences here let the reshape act
	 * on a sample: the heading below was renamed in place, so the document's example
	 * came back as {@code ## Testing} with its indentation gone — a rewrite of the
	 * project's own prose, committed and pushed as part of adopting Claude Code.
	 */
	@Test
	void leavesANearMissHeadingInsideAnIndentedCodeBlockAlone() {
		String generated = """
				# CLAUDE.md

				See AGENTS.md.

				A section skeleton looks like this:

				    ## Testing conventions

				    Write unit tests for all new logic.
				""";
		String conformed = conformer.conform(generated);
		assertTrue(conformed.contains("    ## Testing conventions"),
				"the indented sample must be left untouched:\n" + conformed);
		assertTrue(headings(conformed).contains("## Testing"),
				"the section the sample only illustrates must still be appended:\n" + conformed);
	}

	/**
	 * A body shown as an indented sample is code, and the rule counts a code block as
	 * a section's body — so the section is not empty and must not be given a stub in
	 * front of the body it already carries.
	 */
	@Test
	void countsAnIndentedCodeBlockAsASectionBody() {
		String generated = """
				# CLAUDE.md

				See AGENTS.md.

				## Testing

				    mvn -pl adopt -am test
				""";
		String conformed = conformer.conform(generated);
		List<String> body = conformed.lines().dropWhile(line -> !line.equals("## Testing")).skip(1)
				.dropWhile(String::isBlank).toList();
		assertEquals("    mvn -pl adopt -am test", body.getFirst(),
				"the indented body must stay the section's first content:\n" + conformed);
	}

	@Test
	void addsTheAgentsReferenceWhenTheOnlyMentionIsInsideAFence() {
		String generated = """
				# CLAUDE.md

				```markdown
				See AGENTS.md.
				```

				## Project

				A repo.
				""";
		String conformed = conformer.conform(generated);
		assertTrue(referencesAgentsMdOutsideFences(conformed),
				"a mention that only exists as a code sample does not satisfy the rule:\n" + conformed);
	}

	/** Mirrors how the rule looks for the reference: fenced lines do not count. */
	private boolean referencesAgentsMdOutsideFences(String content) {
		return linesOutsideFences(content).stream()
				.anyMatch(line -> line.contains(ClaudeMdConformer.AGENTS_REFERENCE));
	}

	@Test
	void buildsAWholeSkeletonFromAnEmptyDocument() {
		String conformed = conformer.conform("");
		assertEquals(ClaudeMdConformer.TITLE, conformed.lines().findFirst().orElseThrow());
		assertTrue(headings(conformed).containsAll(ClaudeMdConformer.REQUIRED_SECTIONS), conformed);
		ClaudeMdConformer.REQUIRED_SECTIONS.forEach(section -> assertTrue(hasBody(conformed, section),
				section + " must have a body:\n" + conformed));
	}

	@Test
	void collapsesTrailingBlankLinesToASingleNewline() {
		String conforming = ("# CLAUDE.md\n\n" + ClaudeMdConformer.AGENTS_REFERENCE_LINE + "\n\n"
				+ requiredSectionsBody()).stripTrailing() + "\n";
		String conformed = conformer.conform(conforming + "\n\n\n");
		assertEquals(conforming, conformed, "trailing blank lines must be normalised away");
	}

	private String requiredSectionsBody() {
		return ClaudeMdConformer.REQUIRED_SECTIONS.stream()
				.map(section -> section + "\n\nContent.\n\n")
				.collect(Collectors.joining());
	}

	private static final String UNTERMINATED_FENCE = """
			# CLAUDE.md

			Intro.

			```java
			class Foo {}
			""";

	/**
	 * A fence the generated document opened and never closed used to swallow every
	 * appended section: the rule reads fences the same way the conformer does, so it
	 * saw the headings as code and reported all six sections missing, failing the
	 * adoption at its own verification step.
	 */
	@Test
	void closesAnUnterminatedFenceSoAppendedSectionsStayDocumentStructure() {
		String conformed = conformer.conform(UNTERMINATED_FENCE);
		assertTrue(headingsOutsideFences(conformed).containsAll(ClaudeMdConformer.REQUIRED_SECTIONS),
				"every appended section must be a heading, not code:\n" + conformed);
		assertTrue(conformed.contains("class Foo {}"), "the code block keeps its content:\n" + conformed);
	}

	/**
	 * The sections appended below an unterminated fence were invisible to the next
	 * run's own check for them, so re-adopting a repository appended a second — and
	 * then a third — unreachable copy of the whole skeleton.
	 */
	@Test
	void reshapingADocumentWithAnUnterminatedFenceIsIdempotent() {
		String once = conformer.conform(UNTERMINATED_FENCE);
		assertEquals(once, conformer.conform(once), "a second reshape must not append the sections again");
	}

	/**
	 * A conforming document that shows what a fence looks like, four columns in,
	 * directly below the sentence introducing it — with no blank line, so no code
	 * block opens at the indent either. It is a lazy continuation of that paragraph
	 * and opens nothing.
	 */
	private static final String INDENTED_FENCE_BELOW_A_PARAGRAPH = """
			# CLAUDE.md

			See [AGENTS.md](AGENTS.md) for the companion agent guide.

			## Project

			A block is delimited by three backticks:
			    ```

			## Java version

			Java 25.

			## Maven

			Root pom is packaging=pom.

			## Principles for Java Development

			SOLID.

			## Testing

			JUnit 5.

			## Dependencies

			Existing only.
			""";

	/**
	 * Reading that delimiter as a fence opened a block nothing closed, so the reshape
	 * saw every heading below it as code: it appended a closing delimiter of its own
	 * and a second copy of the five sections the document already carried, then
	 * committed and pushed the result. The rule read it as conforming because it made
	 * the same mistake, so the verification never caught it.
	 */
	@Test
	void leavesAConformingDocumentWithAnIndentedFenceBelowAParagraphAlone() {
		assertEquals(INDENTED_FENCE_BELOW_A_PARAGRAPH, conformer.conform(INDENTED_FENCE_BELOW_A_PARAGRAPH));
	}

	/** Each required section is written once, so no reader meets two of any of them. */
	@Test
	void doesNotDuplicateSectionsBelowAnIndentedFence() {
		List<String> headings = headings(conformer.conform(INDENTED_FENCE_BELOW_A_PARAGRAPH));
		assertEquals(headings.stream().distinct().toList(), headings, "no heading may be written twice: " + headings);
	}

	/** Mirrors how the rule finds headings: a heading inside a fence is code, not structure. */
	private List<String> headingsOutsideFences(String content) {
		return linesOutsideFences(content).stream().filter(line -> line.startsWith("#")).toList();
	}

	/**
	 * A {@code ````} wrapper holding a {@code ```} sample is one code block to the
	 * rule, which ends a fence only on a run at least as long as the one that opened
	 * it. Reading the inner {@code ```} as the wrapper's end left the sample's
	 * {@code ## Testing} looking like the document's own section, so the real one was
	 * never appended and the rule the adoption had just wired in failed the build.
	 */
	@Test
	void doesNotTakeAHeadingInsideANestedFenceForASection() {
		String generated = """
				# CLAUDE.md

				See AGENTS.md.

				## Project

				How to write a section:

				````
				```
				## Testing

				Run the tests.
				```
				````
				""";
		String conformed = conformer.conform(generated);
		assertTrue(headingsOutsideFences(conformed).contains("## Testing"),
				"the sample's heading is code, so the real section must still be appended:\n" + conformed);
		assertTrue(headingsOutsideFences(conformed).containsAll(ClaudeMdConformer.REQUIRED_SECTIONS), conformed);
	}

	/**
	 * A fence line carrying an info string opens a block and never closes one, so a
	 * {@code ```java} inside an open {@code ```} block is content. Reading it as the
	 * block's end flipped every line after it from code to structure, exposing the
	 * sample's headings to the reshape — which then renamed them and spliced stub
	 * bodies into the very sample the document was explaining.
	 */
	@Test
	void doesNotCloseAFenceWithAnInfoStringDelimiter() {
		String generated = """
				# CLAUDE.md

				See AGENTS.md.

				## Project

				```
				```java
				## Maven
				```

				## Java version

				Java 25.
				""";
		String conformed = conformer.conform(generated);
		assertTrue(headingsOutsideFences(conformed).contains("## Maven"),
				"the sample's heading is code, so the real section must still be appended:\n" + conformed);
		assertTrue(conformed.contains("```java\n## Maven\n"), "the sample must survive verbatim:\n" + conformed);
	}

	/**
	 * The delimiter that closes an unterminated fence has to be one that actually
	 * closes it: a {@code ```} line leaves a {@code ````} wrapper open, so every
	 * section appended below would still be code to the rule.
	 */
	@Test
	void closesAnUnterminatedFenceWithADelimiterAsLongAsTheOneThatOpenedIt() {
		String generated = """
				# CLAUDE.md

				Intro.

				````markdown
				```java
				class Foo {}
				""";
		String conformed = conformer.conform(generated);
		assertTrue(headingsOutsideFences(conformed).containsAll(ClaudeMdConformer.REQUIRED_SECTIONS),
				"every appended section must be a heading, not code:\n" + conformed);
		assertEquals(conformed, conformer.conform(conformed), "a second reshape must not append the sections again");
	}

	/**
	 * The document's lines that sit outside a code fence, stripped. The one place
	 * these tests read fences, so the two questions they ask of a reshaped document —
	 * which headings it carries, and whether it references {@code AGENTS.md} — cannot
	 * come to different answers about what is code.
	 *
	 * <p>Deliberately written the way the enforcer's {@code MarkdownDocument} reads
	 * fences rather than the way {@link ClaudeMdConformer} does, so these tests judge
	 * the reshape against the rule it has to satisfy instead of against its own
	 * reading of the document. A fence is closed only by a run of the same character,
	 * at least as long as the one that opened it, carrying no info string.
	 */
	private List<String> linesOutsideFences(String content) {
		List<String> outside = new ArrayList<>();
		String open = null;
		for (String line : content.lines().map(String::strip).toList()) {
			open = collect(outside, line, open);
		}
		return outside;
	}

	/**
	 * Records the line unless it is code — the delimiters included, as the rule masks
	 * them too — and answers with the fence run still open after it.
	 */
	private String collect(List<String> outside, String line, String open) {
		String run = fenceRun(line);
		if (open == null) {
			addUnlessCode(outside, line, run != null);
			return run;
		}
		return closes(run, line, open) ? null : open;
	}

	private void addUnlessCode(List<String> outside, String line, boolean code) {
		if (!code) {
			outside.add(line);
		}
	}

	private boolean closes(String run, String line, String open) {
		return run != null && run.charAt(0) == open.charAt(0) && run.length() >= open.length()
				&& line.substring(run.length()).isBlank();
	}

	/**
	 * A section the generated document had commented out is not a section the rule
	 * can see, so the reshape has to append the real one. Reading the commented
	 * heading as the document's own left the required section unwritten, and the rule
	 * the adoption had just wired in then reported it missing — the adoption failing
	 * its own verification on a document it had just reshaped to pass it.
	 */
	@Test
	void doesNotTakeAHeadingInsideAnHtmlCommentForASection() {
		String generated = """
				# CLAUDE.md

				See AGENTS.md.

				<!--
				## Testing

				Removed for now.
				-->

				Intro.
				""";
		String conformed = conformer.conform(generated);
		assertTrue(structuralLines(conformed).containsAll(ClaudeMdConformer.REQUIRED_SECTIONS),
				"every required section must be a heading the rule can see:\n" + conformed);
		assertTrue(conformed.contains("Removed for now."), "the commented-out text keeps its content:\n" + conformed);
		assertEquals(conformed, conformer.conform(conformed), "a second reshape must not append the sections again");
	}

	/**
	 * A near-miss heading inside a comment must not be renamed in place either: the
	 * rename rewrote text its author had deliberately commented out and still left
	 * the document without the section, since the rule reads neither as structure.
	 */
	@Test
	void doesNotRenameANearMissHeadingInsideAnHtmlComment() {
		String generated = """
				# CLAUDE.md

				See AGENTS.md.

				<!--
				## Testing strategy

				Old notes.
				-->

				Intro.
				""";
		String conformed = conformer.conform(generated);
		assertTrue(conformed.contains("## Testing strategy"), "the commented heading must be left alone:\n" + conformed);
		assertTrue(structuralLines(conformed).containsAll(ClaudeMdConformer.REQUIRED_SECTIONS),
				"the real section must still be appended:\n" + conformed);
	}

	/**
	 * The rule fails a section whose only content is commented out just as it fails
	 * an empty one, so a stub is owed here. Counting the comment as a body reported
	 * the section as satisfied and the adoption failed its own verification.
	 */
	@Test
	void givesASectionWhoseOnlyBodyIsAnHtmlCommentAStub() {
		String generated = """
				# CLAUDE.md

				See AGENTS.md.

				## Testing

				<!--
				Nothing yet.
				-->

				## Project

				A repo.
				""";
		String conformed = conformer.conform(generated);
		assertTrue(hasStructuralBody(conformed, "## Testing"),
				"a section whose only content is commented out needs a stub:\n" + conformed);
	}

	/**
	 * An unterminated comment swallows everything below it, so the sections appended
	 * there were as invisible to the rule as the ones appended below an unterminated
	 * fence — and as invisible to the next run's own check for them, which appended a
	 * second unreachable copy of the whole skeleton.
	 */
	@Test
	void closesAnUnterminatedHtmlCommentSoAppendedSectionsStayDocumentStructure() {
		String generated = """
				# CLAUDE.md

				See AGENTS.md.

				<!-- work in progress
				## Testing
				""";
		String conformed = conformer.conform(generated);
		assertTrue(structuralLines(conformed).containsAll(ClaudeMdConformer.REQUIRED_SECTIONS),
				"every appended section must be a heading, not commented-out text:\n" + conformed);
		assertEquals(conformed, conformer.conform(conformed), "a second reshape must not append the sections again");
	}

	/** A comment inside a fenced code block is sample text, so the fence wins — as it does for the rule. */
	@Test
	void leavesAHeadingBelowACommentInsideAFenceAsDocumentStructure() {
		String generated = """
				# CLAUDE.md

				See AGENTS.md.

				```markdown
				<!--
				```

				## Testing

				JUnit 5.
				""";
		String conformed = conformer.conform(generated);
		assertTrue(hasStructuralBody(conformed, "## Testing"),
				"the section below the fence is structure, not a comment:\n" + conformed);
		assertFalse(conformed.contains("## Testing\n\n" + "See [AGENTS.md](AGENTS.md)."),
				"the section already had a body:\n" + conformed);
	}

	/**
	 * A delimiter quoted as code is the document illustrating a comment rather than
	 * writing one, which is how the rule reads it. Taking it for a real one opened a
	 * block nothing closed: the reshape appended a closing {@code -->} of its own and
	 * read every heading below the mention as inert, so an already-conforming
	 * {@code CLAUDE.md} came back with a stray delimiter and a second, stubbed copy of
	 * five sections it already carried — committed, pushed, and offered for review.
	 */
	@Test
	void leavesADocumentMentioningACommentDelimiterInAnInlineCodeSpanAlone() {
		String conforming = ("# CLAUDE.md\n\n" + ClaudeMdConformer.AGENTS_REFERENCE_LINE
				+ "\n\nAn HTML comment opens with `<!--`.\n\n" + requiredSectionsBody()).stripTrailing() + "\n";

		assertEquals(conforming, conformer.conform(conforming),
				"a mention of the delimiter is not a comment the reshape has to close");
	}

	/**
	 * A heading is the text it carries rather than the line it was typed on, which is
	 * how the rule reads it: {@code ##  Testing} is the {@code ## Testing} section.
	 * Comparing the line verbatim reported the section as absent and appended a second,
	 * stubbed copy of it to a document that already had one.
	 */
	@Test
	void recognisesARequiredSectionWrittenWithAWiderSeparator() {
		String generated = """
				# CLAUDE.md

				See AGENTS.md.

				##  Testing

				JUnit 5.
				""";
		String conformed = conformer.conform(generated);

		assertEquals(1, headings(conformed).stream()
				.filter(heading -> heading.replaceAll("\\s+", " ").equals("## Testing")).count(),
				"the section the document already carries must not be appended again:\n" + conformed);
		assertFalse(conformed.contains("## Testing\n\n" + ClaudeMdConformer.AGENTS_REFERENCE_LINE),
				"the section already had a body:\n" + conformed);
	}

	/**
	 * A line that merely starts with a hash is prose to the rule, so the section it
	 * sits in already has a body. Reading it as a shallower heading ended the section
	 * above it, and a stub was inserted in front of the content the section carried.
	 */
	@Test
	void treatsALineThatMerelyStartsWithAHashAsSectionBody() {
		String generated = """
				# CLAUDE.md

				See AGENTS.md.

				## Testing

				#1 rule: run mvn install every time.

				## Project

				A repo.
				""";
		String conformed = conformer.conform(generated);
		assertFalse(conformed.contains("## Testing\n\n" + "See [AGENTS.md](AGENTS.md)."),
				"the section already had a body and needs no stub:\n" + conformed);
		assertTrue(conformed.contains("#1 rule: run mvn install every time."),
				"the prose keeps its place:\n" + conformed);
	}

	/**
	 * The document's lines that can carry structure, read the way the enforcer's
	 * {@code MarkdownDocument} reads them: outside code fences and outside HTML
	 * comments alike. The counterpart of {@link #linesOutsideFences} for the tests
	 * that ask what the rule would recognise as a heading.
	 */
	private List<String> structuralLines(String content) {
		List<String> structural = new ArrayList<>();
		boolean open = false;
		for (String line : linesOutsideFences(content)) {
			open = collectStructural(structural, line, open);
		}
		return structural;
	}

	/** Records the line unless a comment covers it, and answers whether one is still open after it. */
	private boolean collectStructural(List<String> structural, String line, boolean open) {
		if (open) {
			return !line.contains("-->");
		}
		boolean opens = line.startsWith("<!--") && !line.contains("-->");
		addUnlessCode(structural, line, opens);
		return opens;
	}

	/** Whether the section carries a body the rule would count: prose outside fences and comments. */
	private boolean hasStructuralBody(String content, String section) {
		List<String> lines = structuralLines(content);
		int start = lines.indexOf(section);
		return start >= 0 && lines.stream().skip(start + 1L).dropWhile(String::isEmpty).findFirst()
				.filter(line -> !line.startsWith("## ")).isPresent();
	}

	/** The leading run of fence characters a line declares, or {@code null} when it declares none. */
	private String fenceRun(String line) {
		if (line.isEmpty() || (line.charAt(0) != '`' && line.charAt(0) != '~')) {
			return null;
		}
		char character = line.charAt(0);
		String run = line.substring(0, (int) line.chars().takeWhile(candidate -> candidate == character).count());
		return run.length() < 3 ? null : run;
	}
}
