package io.github.adamw7.tools.markdown;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

/**
 * The reshape as anything but a CLAUDE.md asks for it. The CLAUDE.md contract has
 * its own tests in the adopt module, which also runs the real enforcer rule over
 * the output; these cover what generalising the contract added — another title,
 * no companion document, and no required sections at all.
 */
class MarkdownConformerTest {

	private static final MarkdownContract AGENTS = MarkdownContract.titled("# AGENTS.md")
			.requiring(List.of("## Project overview", "## Module layout"));

	@Test
	void putsTheContractsOwnTitleOnTheFirstLine() {
		String conformed = new MarkdownConformer(AGENTS).conform("Some prose.\n");

		assertTrue(conformed.startsWith("# AGENTS.md\n"), conformed);
	}

	@Test
	void appendsEveryRequiredSectionWithABody() {
		String conformed = new MarkdownConformer(AGENTS).conform("# AGENTS.md\n");

		assertTrue(conformed.contains("## Project overview"), conformed);
		assertTrue(conformed.contains("## Module layout"), conformed);
		assertTrue(conformed.contains(MarkdownContract.DEFAULT_STUB_BODY), conformed);
	}

	/** A near miss is renamed in place, so whatever the author wrote under it survives. */
	@Test
	void renamesANearMissAndKeepsItsBody()  {
		String conformed = new MarkdownConformer(AGENTS)
				.conform("# AGENTS.md\n\n## Project overview and goals\n\nWhat this is.\n");

		assertTrue(conformed.contains("## Project overview\n"), conformed);
		assertTrue(conformed.contains("What this is."), conformed);
		assertFalse(conformed.contains("## Project overview and goals"), conformed);
	}

	/** A contract naming no companion document must not invent a reference to one. */
	@Test
	void insertsNoReferenceWhenTheContractNamesNoCompanionDocument() {
		String conformed = new MarkdownConformer(AGENTS).conform("# AGENTS.md\n\nProse.\n");

		assertFalse(conformed.contains("See ["), conformed);
	}

	@Test
	void insertsTheReferenceTheContractNames() {
		MarkdownContract contract = MarkdownContract.titled("# CLAUDE.md").referencing("HANDBOOK.md");

		String conformed = new MarkdownConformer(contract).conform("# CLAUDE.md\n\nProse.\n");

		assertTrue(conformed.contains("[HANDBOOK.md](HANDBOOK.md)"), conformed);
	}

	@Test
	void leavesAReferenceTheDocumentAlreadyMakes() {
		MarkdownContract contract = MarkdownContract.titled("# CLAUDE.md").referencing("HANDBOOK.md");

		String conformed = new MarkdownConformer(contract).conform("# CLAUDE.md\n\nSee HANDBOOK.md for detail.\n");

		assertEquals("# CLAUDE.md\n\nSee HANDBOOK.md for detail.\n", conformed);
	}

	/** A contract requiring nothing must not stamp headings onto a document nobody asked to shape. */
	@Test
	void addsNoSectionsWhenTheContractRequiresNone() {
		MarkdownContract contract = MarkdownContract.titled("# NOTES.md");

		String conformed = new MarkdownConformer(contract).conform("# NOTES.md\n\nJust prose.\n");

		assertEquals("# NOTES.md\n\nJust prose.\n", conformed);
	}

	@Test
	void reshapingAnAlreadyConformingDocumentChangesNothing() {
		String once = new MarkdownConformer(AGENTS).conform("# AGENTS.md\n\nProse.\n");

		assertEquals(once, new MarkdownConformer(AGENTS).conform(once));
	}

	/** A section named twice is one section, so a second near miss is not renamed to it. */
	@Test
	void countsARepeatedRequiredSectionOnce() {
		MarkdownContract contract = MarkdownContract.titled("# X.md")
				.requiring(List.of("## Testing", "## Testing"));

		String conformed = new MarkdownConformer(contract).conform("# X.md\n");

		assertEquals(1, conformed.split("## Testing", -1).length - 1, conformed);
	}

	@Test
	void stubBodyIsTheContractsToChoose() {
		MarkdownContract contract = MarkdownContract.titled("# X.md")
				.requiring(List.of("## Testing"))
				.withStubBody("TBD.");

		assertTrue(new MarkdownConformer(contract).conform("# X.md\n").contains("TBD."));
	}

	/** A title the document carries lower down is moved up rather than duplicated. */
	@Test
	void movesATitleTheDocumentCarriesLowerDownToTheFirstLine() {
		MarkdownContract contract = MarkdownContract.titled("# AGENTS.md");

		String conformed = new MarkdownConformer(contract).conform("Intro.\n\n# AGENTS.md\n\nBody.\n");

		assertEquals("# AGENTS.md\n\nIntro.\n\nBody.\n", conformed);
	}

	/**
	 * The blank line under a heading that is removed goes with it only where the line
	 * above was blank too. Here it was not, so dropping it would have run the paragraph
	 * above the title into the one below it.
	 */
	@Test
	void keepsTheBlankBelowAMovedTitleWhenTheLineAboveItIsNotBlank() {
		MarkdownContract contract = MarkdownContract.titled("# AGENTS.md");

		String conformed = new MarkdownConformer(contract).conform("Intro.\n# AGENTS.md\n\nBody.\n");

		assertEquals("# AGENTS.md\n\nIntro.\n\nBody.\n", conformed);
	}

	/**
	 * A second title is a duplicate a check accepts, so nothing downstream would report
	 * it and the reshape being idempotent means running it again never clears it.
	 */
	@Test
	void removesASecondTitleFromADocumentThatAlreadyOpensWithOne() {
		MarkdownContract contract = MarkdownContract.titled("# AGENTS.md");

		String conformed = new MarkdownConformer(contract)
				.conform("# AGENTS.md\n\nIntro.\n\n# AGENTS.md\n\nBody.\n");

		assertEquals("# AGENTS.md\n\nIntro.\n\nBody.\n", conformed);
	}

	/** Renaming in place settles the section, so no second one is appended beside it. */
	@Test
	void renamesAHeadingCarryingExtraWordsWithoutAppendingASecondSection() {
		MarkdownContract contract = MarkdownContract.titled("# X.md").requiring(List.of("## Testing"));

		String conformed = new MarkdownConformer(contract)
				.conform("# X.md\n\n## Testing conventions\n\nHow we test.\n");

		assertEquals("# X.md\n\n## Testing\n\nHow we test.\n", conformed);
	}

	/** Which case a near miss was typed in does not decide whether it is one. */
	@Test
	void renamesAHeadingThatDiffersFromTheRequiredOneOnlyInCase() {
		MarkdownContract contract = MarkdownContract.titled("# X.md").requiring(List.of("## Testing"));

		String conformed = new MarkdownConformer(contract)
				.conform("# X.md\n\n## testing\n\nHow we test.\n");

		assertEquals("# X.md\n\n## Testing\n\nHow we test.\n", conformed);
	}

	/** The space a near match demands after the required wording keeps an unrelated heading its own. */
	@Test
	void leavesAHeadingThatMerelyBeginsWithTheRequiredWording() {
		MarkdownContract contract = MarkdownContract.titled("# X.md").requiring(List.of("## Maven"));

		String conformed = new MarkdownConformer(contract).conform("# X.md\n\n## Mavenish\n\nProse.\n");

		assertTrue(conformed.contains("## Mavenish\n\nProse."), conformed);
		assertTrue(conformed.contains("## Maven\n\n" + MarkdownContract.DEFAULT_STUB_BODY), conformed);
	}

	/**
	 * A heading that already is a required section is reserved, so the search for a near
	 * match for another one cannot rename it and take its body with it.
	 */
	@Test
	void doesNotRenameAHeadingThatIsItselfARequiredSection() {
		MarkdownContract contract = MarkdownContract.titled("# X.md")
				.requiring(List.of("## Build", "## Build steps"));

		String conformed = new MarkdownConformer(contract)
				.conform("# X.md\n\n## Build steps\n\nHow to build.\n");

		assertTrue(conformed.contains("## Build steps\n\nHow to build."), conformed);
		assertTrue(conformed.contains("## Build\n\n" + MarkdownContract.DEFAULT_STUB_BODY), conformed);
	}

	/** A check fails an empty section as firmly as a missing one, so a bare heading is given a body. */
	@Test
	void stubsARequiredHeadingTheDocumentLeavesBare() {
		MarkdownContract contract = MarkdownContract.titled("# X.md").requiring(List.of("## Testing"));

		String conformed = new MarkdownConformer(contract)
				.conform("# X.md\n\n## Testing\n\n## Other\n\nx.\n");

		assertEquals("# X.md\n\n## Testing\n\n" + MarkdownContract.DEFAULT_STUB_BODY
				+ "\n\n## Other\n\nx.\n", conformed);
	}

	/** A section appended inside a fence the document left open is code, which a check reads as absent. */
	@Test
	void closesAnUnterminatedFenceSoAnAppendedSectionIsStructure() {
		MarkdownContract contract = MarkdownContract.titled("# X.md").requiring(List.of("## Testing"));

		String conformed = new MarkdownConformer(contract).conform("# X.md\n\n```\ncode\n");

		assertTrue(MarkdownDocument.parse(conformed).headings().contains("## Testing"), conformed);
	}

	/** The same for a comment the document left open: text appended inside one is inert. */
	@Test
	void closesAnUnterminatedCommentSoAnAppendedSectionIsStructure() {
		MarkdownContract contract = MarkdownContract.titled("# X.md").requiring(List.of("## Testing"));

		String conformed = new MarkdownConformer(contract).conform("# X.md\n\n<!-- note\n");

		assertTrue(MarkdownDocument.parse(conformed).headings().contains("## Testing"), conformed);
	}

	/** The reference is separated from the line below it, which here is prose. */
	@Test
	void separatesTheReferenceFromTheProseItIsInsertedAbove() {
		MarkdownContract contract = MarkdownContract.titled("# CLAUDE.md").referencing("AGENTS.md");

		String conformed = new MarkdownConformer(contract).conform("# CLAUDE.md\nProse.\n");

		assertEquals("# CLAUDE.md\n\n" + contract.referenceLine() + "\n\nProse.\n", conformed);
	}

	/** A blank line already under the title is the separator, so a second one is not added. */
	@Test
	void leavesTheBlankAlreadyUnderTheTitleAsTheReferencesSeparator() {
		MarkdownContract contract = MarkdownContract.titled("# CLAUDE.md").referencing("AGENTS.md");

		String conformed = new MarkdownConformer(contract).conform("# CLAUDE.md\n\nProse.\n");

		assertEquals("# CLAUDE.md\n\n" + contract.referenceLine() + "\n\nProse.\n", conformed);
	}

	/** A document that is nothing but its title has no line below it to read. */
	@Test
	void insertsTheReferenceIntoADocumentWithNothingBelowItsTitle() {
		MarkdownContract contract = MarkdownContract.titled("# CLAUDE.md").referencing("AGENTS.md");

		String conformed = new MarkdownConformer(contract).conform("# CLAUDE.md");

		assertEquals("# CLAUDE.md\n\n" + contract.referenceLine() + "\n", conformed);
	}

	/**
	 * A document nothing settles comes back as the last pass left it rather than looping.
	 * An empty stub body never gives the section it is inserted under a body, so every
	 * pass inserts another: the first appends both sections with a blank stub between
	 * them, and each further pass the bound allows adds another pair of blank lines
	 * there. Nine of them is that bound — one pass, then three more.
	 */
	@Test
	void comesBackAsTheLastPassLeftItWhenNothingSettles() {
		MarkdownContract contract = MarkdownContract.titled("# X.md")
				.requiring(List.of("## A", "## B"))
				.withStubBody("");

		String conformed = new MarkdownConformer(contract).conform("# X.md\n");

		assertEquals(9, blankLinesBetweenTheSections(conformed), conformed);
	}

	/** The blank lines between the two appended sections, less the newline ending the first heading. */
	private static long blankLinesBetweenTheSections(String conformed) {
		String between = conformed.substring(conformed.indexOf("## A"), conformed.indexOf("## B"));
		return between.chars().filter(character -> character == '\n').count() - 1;
	}
}
