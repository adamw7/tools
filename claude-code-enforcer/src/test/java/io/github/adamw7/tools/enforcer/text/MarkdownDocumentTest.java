package io.github.adamw7.tools.enforcer.text;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Set;
import java.util.stream.IntStream;

import org.junit.jupiter.api.Test;

/**
 * Asserts the code mask line by line rather than through a rule's pass or fail,
 * because every structural check the rules make — headings, section bodies,
 * forbidden tokens, line lengths, file references, and the memory imports
 * {@code ImportGraph} reads — is a question asked of this mask. A delimiter
 * mistaken for the end of a block, or an indent read as prose, silently
 * reclassifies whole passages, so the exact set of code lines is what needs
 * pinning.
 */
class MarkdownDocumentTest {

	@Test
	void masksAFencedBlockIncludingBothOfItsDelimiters() {
		MarkdownDocument document = MarkdownDocument.parse("""
				# Title

				```
				int x;
				```

				## Section
				""");

		assertEquals(List.of(2, 3, 4), codeLines(document));
	}

	@Test
	void anAnnotatedBacktickLineDoesNotCloseABacktickFence() {
		MarkdownDocument document = MarkdownDocument.parse("""
				# Title

				```
				example:
				```java
				int x;
				```

				## Section
				body
				""");

		// Lines 2 to 6 are the whole block: ```java is an opening delimiter, so it
		// cannot end it and leave line 6's ``` to open a second fence.
		assertEquals(List.of(2, 3, 4, 5, 6), codeLines(document));
		assertEquals(Set.of("# Title", "## Section"), document.headings());
	}

	@Test
	void aShorterFenceDoesNotCloseALongerOne() {
		MarkdownDocument document = MarkdownDocument.parse("""
				# Title

				````markdown
				```java
				int x;
				```
				````

				## Section
				body
				""");

		assertEquals(List.of(2, 3, 4, 5, 6), codeLines(document));
		assertEquals(Set.of("# Title", "## Section"), document.headings());
	}

	@Test
	void aLongerFenceClosesAShorterOne() {
		MarkdownDocument document = MarkdownDocument.parse("""
				```
				int x;
				`````

				## Section
				""");

		assertEquals(List.of(0, 1, 2), codeLines(document));
		assertTrue(document.hasHeading("## Section"));
	}

	@Test
	void aTildeLineDoesNotCloseABacktickFence() {
		MarkdownDocument document = MarkdownDocument.parse("""
				```
				~~~
				```

				# After
				""");

		assertEquals(List.of(0, 1, 2), codeLines(document));
		assertTrue(document.hasHeading("# After"));
	}

	@Test
	void aClosingDelimiterMayCarryTrailingWhitespace() {
		// Written without a text block, which would strip the trailing spaces away.
		MarkdownDocument document = MarkdownDocument.parse("```\nint x;\n```   \n\n## Section\n");

		assertEquals(List.of(0, 1, 2), codeLines(document));
		assertTrue(document.hasHeading("## Section"));
	}

	@Test
	void anUnclosedFenceMasksEverythingBelowIt() {
		MarkdownDocument document = MarkdownDocument.parse("""
				# Title

				```
				int x;
				## Section
				""");

		assertEquals(List.of(2, 3, 4), codeLines(document));
		assertFalse(document.hasHeading("## Section"));
	}

	@Test
	void aRunOfFewerThanThreeMarkersIsNotAFence() {
		MarkdownDocument document = MarkdownDocument.parse("""
				# Title

				``inline``

				## Section
				""");

		assertEquals(List.of(), codeLines(document));
		assertEquals(Set.of("# Title", "## Section"), document.headings());
	}

	@Test
	void findsATokenOnTheContentThatFollowsANestedFence() {
		MarkdownDocument document = MarkdownDocument.parse("""
				```
				```java
				```

				TODO left behind
				""");

		assertEquals(List.of(0, 1, 2), codeLines(document));
		assertTrue(document.containsInProse("TODO"));
	}

	@Test
	void doesNotFindATokenWrappedByALongerFence() {
		MarkdownDocument document = MarkdownDocument.parse("""
				````markdown
				```java
				TODO in an example
				```
				````
				""");

		assertFalse(document.containsInProse("TODO"));
	}

	/**
	 * The other way Markdown quotes a sample. The blank line between the two chunks
	 * separates them rather than ending the block, and carries nothing either way, so
	 * it is left unmasked.
	 */
	@Test
	void masksAnIndentedCodeBlock() {
		MarkdownDocument document = MarkdownDocument.parse("""
				# Title

				An example of the section a template adds:

				    ## Testing

				    Write tests.

				## Maven
				Body.
				""");

		assertEquals(List.of(4, 6), codeLines(document));
		assertEquals(Set.of("# Title", "## Maven"), document.headings());
	}

	@Test
	void masksATabIndentedBlock() {
		// Written without a text block, so the single leading tab is unambiguous.
		MarkdownDocument document = MarkdownDocument.parse("# Title\n\n\tTODO in a sample\n\n## Maven\nBody.\n");

		assertEquals(List.of(2), codeLines(document));
		assertFalse(document.containsInProse("TODO"));
	}

	/** A forbidden token inside an indented sample is the sample's, not the document's. */
	@Test
	void doesNotFindATokenInsideAnIndentedBlock() {
		MarkdownDocument document = MarkdownDocument.parse("""
				# Title

				For example:

				    TODO left in a sample

				## Maven
				Body.
				""");

		assertFalse(document.containsInProse("TODO"));
	}

	/**
	 * An indented code block cannot interrupt a paragraph: the indented line below one
	 * is a lazy continuation of it. Masking it would hide prose the document really
	 * does say.
	 */
	@Test
	void treatsAnIndentedLineBelowAParagraphAsPartOfThatParagraph() {
		MarkdownDocument document = MarkdownDocument.parse("""
				# Title

				A wrapped sentence
				    continued with a deep indent.

				## Maven
				Body.
				""");

		assertEquals(List.of(), codeLines(document));
		assertTrue(document.containsInProse("continued"));
	}

	/** Three spaces is the deepest a heading may be indented and stay one. */
	@Test
	void doesNotTreatAThreeSpaceIndentAsCode() {
		MarkdownDocument document = MarkdownDocument.parse("""
				# Title

				   ## Testing

				Body.
				""");

		assertEquals(List.of(), codeLines(document));
		assertTrue(document.hasHeading("## Testing"));
	}

	/**
	 * A lone {@code ```} shown four columns in is how a document explains what a
	 * fence looks like, not a fence this document opens. Reading the fences first and
	 * the indents afterwards let that delimiter open a block nothing closed, which
	 * masked every line below it — the document's remaining headings included — as
	 * code.
	 */
	@Test
	void doesNotOpenAFenceOnADelimiterShownInsideAnIndentedBlock() {
		MarkdownDocument document = MarkdownDocument.parse("""
				# Title

				A block is delimited by three backticks:

				    ```

				## Testing
				Body.
				""");

		assertEquals(List.of(4), codeLines(document));
		assertEquals(Set.of("# Title", "## Testing"), document.headings());
		assertTrue(document.hasBody("## Testing"));
	}

	/** A balanced pair shown that way is the sample's too, so nothing below it changes. */
	@Test
	void readsABalancedFencePairInsideAnIndentedBlockAsThatBlock() {
		MarkdownDocument document = MarkdownDocument.parse("""
				# Title

				Write it like this:

				    ```java
				    int x;
				    ```

				## Testing
				Body.
				""");

		assertEquals(List.of(4, 5, 6), codeLines(document));
		assertEquals(Set.of("# Title", "## Testing"), document.headings());
	}

	/** An indented block is content, so a section whose only body is one is not empty. */
	@Test
	void readsAnIndentedBlockAsASectionBody() {
		MarkdownDocument document = MarkdownDocument.parse("""
				# Title

				## Maven

				    mvn install

				## Testing
				Body.
				""");

		assertTrue(document.hasBody("## Maven"));
	}

	@Test
	void doesNotTreatAHashPrefixedProseLineAsAHeading() {
		MarkdownDocument document = MarkdownDocument.parse("""
				# Title

				## Maven

				#1 rule: run mvn install.

				## Testing

				Body.
				""");

		// "#1 rule:" is prose, not an ATX heading. Counting it as one would end the
		// Maven section at its first line and report the section as empty.
		assertEquals(Set.of("# Title", "## Maven", "## Testing"), document.headings());
		assertTrue(document.hasBody("## Maven"));
	}

	@Test
	void doesNotTreatSevenHashesAsAHeading() {
		MarkdownDocument document = MarkdownDocument.parse("# Title\n\n####### too deep\n");

		assertEquals(Set.of("# Title"), document.headings());
	}

	@Test
	void treatsABareHashAsAHeading() {
		MarkdownDocument document = MarkdownDocument.parse("# Title\n\n##\n\nbody\n");

		assertEquals(Set.of("# Title", "##"), document.headings());
	}

	@Test
	void doesNotTreatAHeadingInsideAnHtmlCommentAsAHeading() {
		MarkdownDocument document = MarkdownDocument.parse("""
				# Title

				<!--
				## Testing
				Body.
				-->

				## Maven
				Body.
				""");

		assertEquals(Set.of("# Title", "## Maven"), document.headings());
	}

	@Test
	void treatsAHeadingAfterAClosedHtmlCommentAsAHeading() {
		MarkdownDocument document = MarkdownDocument.parse("""
				# Title

				<!--
				A note.
				-->

				## Testing
				Body.
				""");

		assertEquals(Set.of("# Title", "## Testing"), document.headings());
	}

	/** A comment that opens and closes on one line masks nothing, and is no heading either. */
	@Test
	void leavesASingleLineHtmlCommentUnmasked() {
		MarkdownDocument document = MarkdownDocument.parse("""
				# Title

				<!-- ## Testing -->

				## Maven
				Body.
				""");

		assertEquals(Set.of("# Title", "## Maven"), document.headings());
	}

	@Test
	void treatsAnHtmlCommentInsideAFenceAsSampleCode() {
		MarkdownDocument document = MarkdownDocument.parse("""
				# Title

				```
				<!--
				```

				## Maven
				Body.
				""");

		assertEquals(Set.of("# Title", "## Maven"), document.headings());
	}

	@Test
	void readsASectionWhoseOnlyContentIsCommentedOutAsEmpty() {
		MarkdownDocument document = MarkdownDocument.parse("""
				# Title

				## Maven
				<!--
				Body.
				-->

				## Testing
				Body.
				""");

		assertFalse(document.hasBody("## Maven"));
		assertTrue(document.hasBody("## Testing"));
	}

	/**
	 * A comment opened after text on its line still hides what follows. Reading only
	 * the first characters of the line left the block unopened, and the heading it
	 * was written to retire went on satisfying the check that demands it.
	 */
	@Test
	void doesNotTreatAHeadingHiddenByACommentOpenedAfterTextAsAHeading() {
		MarkdownDocument document = MarkdownDocument.parse("""
				# Title

				Superseded, kept for reference: <!--
				## Testing
				Body.
				-->

				## Maven
				Body.
				""");

		assertEquals(Set.of("# Title", "## Maven"), document.headings());
	}

	/** A line that closes one comment and opens another leaves the next line commented. */
	@Test
	void keepsACommentOpenedAfterAClosedOneOpen() {
		MarkdownDocument document = MarkdownDocument.parse("""
				# Title

				<!-- a --> <!--
				## Testing
				-->

				## Maven
				Body.
				""");

		assertEquals(Set.of("# Title", "## Maven"), document.headings());
	}

	/** A commented-out mention is inert: it neither satisfies a required token nor trips a forbidden one. */
	@Test
	void doesNotFindATokenInsideAnHtmlComment() {
		MarkdownDocument document = MarkdownDocument.parse("""
				# Title

				<!--
				TODO left behind
				-->
				""");

		assertFalse(document.containsInProse("TODO"));
	}

	@Test
	void marksTheLinesAnHtmlCommentSpansAsCommented() {
		MarkdownDocument document = MarkdownDocument.parse("""
				# Title

				<!--
				A note.
				-->
				Body.
				""");

		assertEquals(List.of(2, 3, 4), commentedLines(document));
	}

	/** The indices of the lines the document masks as code, in document order. */
	private static List<Integer> codeLines(MarkdownDocument document) {
		return IntStream.range(0, document.lineCount()).filter(document::isInsideCode).boxed().toList();
	}

	/** The indices of the lines the document masks as an HTML comment, in document order. */
	private static List<Integer> commentedLines(MarkdownDocument document) {
		return IntStream.range(0, document.lineCount()).filter(document::isInsideComment).boxed().toList();
	}
}
