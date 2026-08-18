package io.github.adamw7.tools.markdown;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

class MarkdownContractTest {

	@Test
	void requiresNoReferenceByDefault() {
		assertFalse(MarkdownContract.titled("# X.md").requiresReference());
	}

	@Test
	void derivesTheConventionalLinkForAReferenceItIsGivenBare() {
		MarkdownContract contract = MarkdownContract.titled("# X.md").referencing("AGENTS.md");

		assertTrue(contract.requiresReference());
		assertTrue(contract.referenceLine().contains("[AGENTS.md](AGENTS.md)"), contract.referenceLine());
	}

	@Test
	void keepsTheReferenceLineItIsGivenVerbatim() {
		MarkdownContract contract = MarkdownContract.titled("# X.md").referencing("AGENTS.md", "Read AGENTS.md.");

		assertEquals("Read AGENTS.md.", contract.referenceLine());
	}

	/** A blank name is a project with no companion document, not a document called "". */
	@Test
	void requiresNoReferenceWhenNamedBlank() {
		assertFalse(MarkdownContract.titled("# X.md").referencing("   ").requiresReference());
		assertFalse(MarkdownContract.titled("# X.md").referencing(null).requiresReference());
	}

	@Test
	void keepsARepeatedRequiredSectionOnceInTheOrderGiven() {
		MarkdownContract contract = MarkdownContract.titled("# X.md")
				.requiring(List.of("## B", "## A", "## B"));

		assertEquals(List.of("## B", "## A"), contract.requiredSections());
	}
}
