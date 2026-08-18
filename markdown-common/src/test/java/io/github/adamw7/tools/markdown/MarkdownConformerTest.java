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
}
