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

	/**
	 * A list item indents its own continuation paragraphs to the column its content
	 * starts at, so four columns from the margin is prose inside a list rather than
	 * code. Reading it as code hid what such a paragraph says from every check that
	 * asks the document what it mentions.
	 */
	@Test
	void treatsAnIndentedParagraphContinuingAListItemAsProse() {
		MarkdownDocument document = MarkdownDocument.parse("""
				# Title

				- the modules are:

				    `data`, `code`, and `adopt` are the reactor modules.

				## Maven
				Body.
				""");

		assertEquals(List.of(), codeLines(document));
		assertTrue(document.containsInProse("adopt"));
	}

	/** Inside a list the code indent is measured from the item's content, not the margin. */
	@Test
	void masksABlockIndentedFourColumnsPastAListItemsContent() {
		MarkdownDocument document = MarkdownDocument.parse("""
				# Title

				- run it with:

				      mvn install

				## Maven
				Body.
				""");

		assertEquals(List.of(4), codeLines(document));
		assertFalse(document.containsInProse("mvn install"));
	}

	/** A paragraph back at the margin ends the list, so the indent is measured from it again. */
	@Test
	void measuresTheCodeIndentFromTheMarginOnceAListHasEnded() {
		MarkdownDocument document = MarkdownDocument.parse("""
				# Title

				- an item.

				A paragraph of its own.

				    TODO left in a sample

				## Maven
				Body.
				""");

		assertEquals(List.of(6), codeLines(document));
		assertFalse(document.containsInProse("TODO"));
	}

	/** A thematic break carries no whitespace after its first dash, so it opens no list. */
	@Test
	void doesNotReadAThematicBreakAsAListItem() {
		MarkdownDocument document = MarkdownDocument.parse("""
				# Title

				---

				    TODO left in a sample

				## Maven
				Body.
				""");

		assertEquals(List.of(4), codeLines(document));
		assertFalse(document.containsInProse("TODO"));
	}

	/** An ordered marker indents its content just as a bullet does. */
	@Test
	void treatsAnIndentedParagraphContinuingAnOrderedItemAsProse() {
		MarkdownDocument document = MarkdownDocument.parse("""
				# Title

				1. the first step:

				    Run the build afterwards.

				## Maven
				Body.
				""");

		assertEquals(List.of(), codeLines(document));
		assertTrue(document.containsInProse("Run the build"));
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

	/**
	 * The same delimiter directly below a paragraph, with no blank line to open a code
	 * block at it. It is a lazy continuation of that paragraph — a fence opener may be
	 * indented at most three columns — so it is neither code nor a fence. Reading it as
	 * one opened a block nothing closed and masked the whole of the document below it,
	 * so {@code claudeMdFormat} reported sections the document plainly carries as
	 * missing.
	 */
	@Test
	void doesNotOpenAFenceOnADelimiterIndentedBelowAParagraph() {
		MarkdownDocument document = MarkdownDocument.parse("""
				# Title

				A block is delimited by three backticks:
				    ```

				## Testing
				Body.
				""");

		assertEquals(List.of(), codeLines(document));
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

	/**
	 * A delimiter quoted as code is the document illustrating a comment rather than
	 * writing one. Reading it as a real one opened a block nothing closed, so every
	 * line below the mention was masked as inert and the sections the document plainly
	 * carries were reported as missing — the same silent reclassification a mis-read
	 * fence produces, reached from a sentence documentation about Markdown routinely
	 * writes.
	 */
	@Test
	void doesNotOpenACommentOnADelimiterInsideAnInlineCodeSpan() {
		MarkdownDocument document = MarkdownDocument.parse("""
				# Title

				An HTML comment opens with `<!--`.

				## Testing
				Body.
				""");

		assertEquals(List.of(), commentedLines(document));
		assertEquals(Set.of("# Title", "## Testing"), document.headings());
		assertTrue(document.hasBody("## Testing"));
	}

	/**
	 * Inside a comment there are no code spans to honour: the text is already inert,
	 * so its backticks are ordinary characters and the {@code -->} among them closes
	 * the block exactly as one anywhere else on the line does.
	 */
	@Test
	void closesACommentOnADelimiterWrittenInsideBackticks() {
		MarkdownDocument document = MarkdownDocument.parse("""
				# Title

				<!--
				A note.
				`-->`

				## Testing
				Body.
				""");

		assertEquals(List.of(2, 3, 4), commentedLines(document));
		assertEquals(Set.of("# Title", "## Testing"), document.headings());
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

	/**
	 * A heading is the text it carries, so the closing {@code #} run Markdown lets an
	 * author balance it with is spelling. Matching the raw line reported a document
	 * that has the section as missing it, and gave the section's content to the one
	 * above it.
	 */
	@Test
	void readsAHeadingClosedByATrailingHashRun() {
		MarkdownDocument document = MarkdownDocument.parse("""
				# Title #

				## Testing ##
				Body.
				""");

		assertEquals(Set.of("# Title", "## Testing"), document.headings());
		assertTrue(document.hasBody("## Testing"));
	}

	/** Only whitespace makes a trailing run a closing one, so a hash inside the text stays in it. */
	@Test
	void keepsATrailingHashThatNoWhitespacePrecedes() {
		MarkdownDocument document = MarkdownDocument.parse("""
				# Title

				## C#
				Body.
				""");

		assertEquals(Set.of("# Title", "## C#"), document.headings());
	}

	@Test
	void readsAHeadingThatIsNothingButItsClosingRun() {
		MarkdownDocument document = MarkdownDocument.parse("""
				# Title

				## ###
				Body.
				""");

		assertEquals(Set.of("# Title", "##"), document.headings());
	}

	/** A tab separates a heading from its text as a space does, so the two name one heading. */
	@Test
	void readsAHeadingSeparatedByATab() {
		MarkdownDocument document = MarkdownDocument.parse("# Title\n\n##\tTesting\nBody.\n");

		assertEquals(Set.of("# Title", "## Testing"), document.headings());
		assertTrue(document.hasBody("## Testing"));
	}

	/**
	 * The order comparison reads headings alone. Reading every structural line let a
	 * wanted entry that is no heading — a section configured without its {@code ##} —
	 * be answered by a line of prose, so a document was reported both as missing the
	 * section and as having it out of order.
	 */
	@Test
	void headingsInOrderIgnoresAProseLineThatMatchesAWantedEntry() {
		MarkdownDocument document = MarkdownDocument.parse("""
				# Title

				Testing

				## Testing
				Body.
				""");

		assertEquals(List.of("## Testing"), document.headingsInOrder(List.of("Testing", "## Testing")));
	}

	@Test
	void headingsInOrderListsTheWantedHeadingsAsTheDocumentOrdersThem() {
		MarkdownDocument document = MarkdownDocument.parse("""
				# Title

				## Maven
				Body.

				## Testing ##
				Body.
				""");

		// The document's order, not the wanted list's, and a closed heading counts.
		assertEquals(List.of("## Maven", "## Testing"),
				document.headingsInOrder(List.of("## Testing", "## Maven")));
	}

	/** A token quoted as code is the document illustrating it, exactly as a fenced sample is. */
	@Test
	void doesNotReadATokenInsideAnInlineCodeSpanAsUnquoted() {
		MarkdownDocument document = MarkdownDocument.parse("""
				# Title

				Never leave a `TODO` behind.
				""");

		assertFalse(document.containsUnquoted("TODO"));
		assertTrue(document.containsInProse("TODO"));
	}

	@Test
	void readsATokenOutsideACodeSpanOnTheSameLineAsUnquoted() {
		MarkdownDocument document = MarkdownDocument.parse("""
				# Title

				A `TODO` marker: TODO finish this.
				""");

		assertTrue(document.containsUnquoted("TODO"));
	}

	@Test
	void doesNotReadATokenInsideACodeFenceOrAnHtmlCommentAsUnquoted() {
		MarkdownDocument document = MarkdownDocument.parse("""
				# Title

				```
				TODO in a sample
				```

				<!--
				TODO removed
				-->
				""");

		assertFalse(document.containsUnquoted("TODO"));
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
