package io.github.adamw7.tools.enforcer.text;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Set;
import java.util.stream.IntStream;

import org.junit.jupiter.api.Test;

/**
 * Asserts the fenced-code-block mask line by line rather than through a rule's
 * pass or fail, because every structural check the rules make — headings,
 * section bodies, forbidden tokens, line lengths, file references, and the
 * memory imports {@code ImportGraph} reads — is a question asked of this mask.
 * A delimiter mistaken for the end of a block silently reclassifies everything
 * after it, so the exact set of code lines is what needs pinning.
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

		assertEquals(List.of(2, 3, 4), fencedLines(document));
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
		assertEquals(List.of(2, 3, 4, 5, 6), fencedLines(document));
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

		assertEquals(List.of(2, 3, 4, 5, 6), fencedLines(document));
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

		assertEquals(List.of(0, 1, 2), fencedLines(document));
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

		assertEquals(List.of(0, 1, 2), fencedLines(document));
		assertTrue(document.hasHeading("# After"));
	}

	@Test
	void aClosingDelimiterMayCarryTrailingWhitespace() {
		// Written without a text block, which would strip the trailing spaces away.
		MarkdownDocument document = MarkdownDocument.parse("```\nint x;\n```   \n\n## Section\n");

		assertEquals(List.of(0, 1, 2), fencedLines(document));
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

		assertEquals(List.of(2, 3, 4), fencedLines(document));
		assertFalse(document.hasHeading("## Section"));
	}

	@Test
	void aRunOfFewerThanThreeMarkersIsNotAFence() {
		MarkdownDocument document = MarkdownDocument.parse("""
				# Title

				``inline``

				## Section
				""");

		assertEquals(List.of(), fencedLines(document));
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

		assertEquals(List.of(0, 1, 2), fencedLines(document));
		assertTrue(document.containsOnStructuralLine("TODO"));
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

		assertFalse(document.containsOnStructuralLine("TODO"));
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

	@Test
	void doesNotFindATokenInsideAnHtmlComment() {
		MarkdownDocument document = MarkdownDocument.parse("""
				# Title

				<!--
				TODO dropped along with the section that used it.
				-->

				Body.
				""");

		assertFalse(document.containsOnStructuralLine("TODO"));
	}

	@Test
	void doesNotCountACommentedLineAsCarryingStructure() {
		MarkdownDocument document = MarkdownDocument.parse("""
				# Title

				<!--
				@docs/removed.md
				-->

				@docs/kept.md
				""");

		assertEquals(List.of(0, 1, 5, 6), structuralLines(document));
	}

	/** The indices of the lines the document masks as fenced code, in document order. */
	private static List<Integer> fencedLines(MarkdownDocument document) {
		return IntStream.range(0, document.lineCount()).filter(document::isInsideFence).boxed().toList();
	}

	/** The indices of the lines that carry document structure, in document order. */
	private static List<Integer> structuralLines(MarkdownDocument document) {
		return IntStream.range(0, document.lineCount()).filter(document::carriesStructure).boxed().toList();
	}
}
