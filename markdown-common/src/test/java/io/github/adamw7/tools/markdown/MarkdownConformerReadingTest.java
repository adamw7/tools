package io.github.adamw7.tools.markdown;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;

/**
 * How the reshape reads the document it rewrites: a heading inside a fence, an
 * indented sample or an HTML comment is code and is left alone, a code block counts
 * as a section's body, and a document left with an open fence or comment is closed
 * before anything is appended. Asked with the {@code CLAUDE.md} contract, the one the
 * adoption conforms to and the enforcer checks, so a document here is the kind the
 * reshape really meets. The adopt module keeps the tests of that contract's own
 * choices, and runs the real enforcer rule over the output.
 */
class MarkdownConformerReadingTest {

	private static final MarkdownContract CLAUDE_MD = MarkdownContract.titled(ClaudeMdContract.TITLE)
			.requiring(ClaudeMdContract.REQUIRED_SECTIONS)
			.referencing(ClaudeMdContract.COMPANION);

	private static final String TITLE = ClaudeMdContract.TITLE;
	private static final String AGENTS_REFERENCE = ClaudeMdContract.COMPANION;
	private static final String AGENTS_REFERENCE_LINE = CLAUDE_MD.referenceLine();
	private static final List<String> REQUIRED_SECTIONS = ClaudeMdContract.REQUIRED_SECTIONS;
	private static final String STUB_BODY = CLAUDE_MD.stubBody();

	private final MarkdownConformer conformer = new MarkdownConformer(CLAUDE_MD);

	/**
	 * The headings a reshaped document carries, read with {@link MarkdownDocument}, the
	 * reader the reshape rewrites through. Duplicates are kept, unlike
	 * {@link MarkdownDocument#headings()}, because a section written twice is exactly
	 * what some of these tests are about.
	 */
	private List<String> headings(String content) {
		MarkdownDocument document = MarkdownDocument.parse(content);
		return document.structuralLines(line -> MarkdownDocument.headingOf(line).isPresent())
				.mapToObj(document::line)
				.map(MarkdownDocument::headingOf)
				.flatMap(Optional::stream)
				.toList();
	}

	/** Whether the section carries a body, asked exactly as a check asks it. */
	private boolean hasBody(String content, String section) {
		MarkdownDocument document = MarkdownDocument.parse(content);
		int index = document.headingIndex(section);
		return index >= 0 && document.hasBodyAt(index);
	}

	/** A document the reshape has nothing to do to, for the tests that are about what it leaves alone. */
	private String conforming() {
		return ("# CLAUDE.md\n\n" + AGENTS_REFERENCE_LINE + "\n\n"
				+ requiredSectionsBody()).stripTrailing() + "\n";
	}

	private String requiredSectionsBody() {
		return REQUIRED_SECTIONS.stream()
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
	 * Only the document's own title moves. One inside a code sample belongs to the
	 * sample — the rule reads it as code — so removing it would rewrite the sample.
	 */
	@Test
	void leavesATitleInsideACodeFenceWhereItIs() {
		String generated = "A preamble.\n\n```markdown\n# CLAUDE.md\n```\n";
		String conformed = conformer.conform(generated);
		assertEquals(TITLE, conformed.lines().findFirst().orElseThrow());
		assertEquals(2, conformed.lines().filter(TITLE::equals).count(),
				"the sample keeps its line and the document gains a title of its own:\n" + conformed);
		assertTrue(conformed.contains("```markdown"), "the fence survives");
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
		assertEquals(1, conformed.lines().filter(TITLE::equals).count(),
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
		assertFalse(conformed.contains("## Project\n\n" + STUB_BODY),
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
		assertFalse(conformed.contains("## Testing\n\n" + STUB_BODY),
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
		assertTrue(MarkdownDocument.parse(conformed).containsInProse(AGENTS_REFERENCE),
				"a mention that only exists as a code sample does not satisfy the rule:\n" + conformed);
	}

	@Test
	void collapsesTrailingBlankLinesToASingleNewline() {
		String conforming = ("# CLAUDE.md\n\n" + AGENTS_REFERENCE_LINE + "\n\n"
				+ requiredSectionsBody()).stripTrailing() + "\n";
		String conformed = conformer.conform(conforming + "\n\n\n");
		assertEquals(conforming, conformed, "trailing blank lines must be normalised away");
	}

	/**
	 * A fence the generated document opened and never closed used to swallow every
	 * appended section: the rule reads fences the same way the conformer does, so it
	 * saw the headings as code and reported all six sections missing, failing the
	 * adoption at its own verification step.
	 */
	@Test
	void closesAnUnterminatedFenceSoAppendedSectionsStayDocumentStructure() {
		String conformed = conformer.conform(UNTERMINATED_FENCE);
		assertTrue(headings(conformed).containsAll(REQUIRED_SECTIONS),
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
		assertTrue(headings(conformed).contains("## Testing"),
				"the sample's heading is code, so the real section must still be appended:\n" + conformed);
		assertTrue(headings(conformed).containsAll(REQUIRED_SECTIONS), conformed);
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
		assertTrue(headings(conformed).contains("## Maven"),
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
		assertTrue(headings(conformed).containsAll(REQUIRED_SECTIONS),
				"every appended section must be a heading, not code:\n" + conformed);
		assertEquals(conformed, conformer.conform(conformed), "a second reshape must not append the sections again");
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
		assertTrue(headings(conformed).containsAll(REQUIRED_SECTIONS),
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
		assertTrue(headings(conformed).containsAll(REQUIRED_SECTIONS),
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
		assertTrue(hasBody(conformed, "## Testing"),
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
		assertTrue(headings(conformed).containsAll(REQUIRED_SECTIONS),
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
		assertTrue(hasBody(conformed, "## Testing"),
				"the section below the fence is structure, not a comment:\n" + conformed);
		assertFalse(conformed.contains("## Testing\n\n" + STUB_BODY),
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
		String conforming = ("# CLAUDE.md\n\n" + AGENTS_REFERENCE_LINE
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
		assertFalse(conformed.contains("## Testing\n\n" + AGENTS_REFERENCE_LINE),
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
		assertFalse(conformed.contains("## Testing\n\n" + STUB_BODY),
				"the section already had a body and needs no stub:\n" + conformed);
		assertTrue(conformed.contains("#1 rule: run mvn install every time."),
				"the prose keeps its place:\n" + conformed);
	}
}
