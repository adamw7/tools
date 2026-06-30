package io.github.adamw7.tools.enforcer.text;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Optional;

import org.junit.jupiter.api.Test;

class FrontMatterFixerTest {

	@Test
	void leavesWellFormedFrontMatterUntouched() {
		String content = """
				---
				name: reviewer
				description: Reviews code.
				---
				# Reviewer
				""";

		assertTrue(FrontMatterFixer.repair(content).isEmpty());
	}

	@Test
	void insertsAMissingClosingDelimiter() {
		String content = """
				---
				name: reviewer
				description: Reviews code.
				# Reviewer
				""";

		Optional<String> repaired = FrontMatterFixer.repair(content);

		assertTrue(repaired.isPresent());
		assertTrue(FrontMatter.parse(repaired.get()).isPresent(), repaired.get());
		assertTrue(FrontMatter.parse(repaired.get()).get().hasKey("description"), repaired.get());
	}

	@Test
	void normalizesAnOverDashedClosingDelimiter() {
		String content = """
				---
				name: reviewer
				----
				# Reviewer
				""";

		Optional<String> repaired = FrontMatterFixer.repair(content);

		assertTrue(repaired.isPresent());
		assertTrue(FrontMatter.parse(repaired.get()).isPresent(), repaired.get());
	}

	@Test
	void normalizesOverDashedOpeningAndClosingDelimiters() {
		String content = """
				----
				name: reviewer
				description: Reviews code.
				----
				body
				""";

		Optional<String> repaired = FrontMatterFixer.repair(content);

		assertTrue(repaired.isPresent());
		assertTrue(repaired.get().startsWith("---\n"), repaired.get());
		assertTrue(FrontMatter.parse(repaired.get()).get().hasKey("name"), repaired.get());
	}

	@Test
	void doesNotMistakeALoneThematicBreakForFrontMatter() {
		String content = """
				---
				Just some prose, no keys here.
				""";

		assertTrue(FrontMatterFixer.repair(content).isEmpty());
	}

	@Test
	void leavesContentWithoutAFrontMatterIntentUntouched() {
		String content = """
				# Title

				Body text.
				""";

		assertTrue(FrontMatterFixer.repair(content).isEmpty());
	}

	@Test
	void preservesTheAbsenceOfATrailingNewline() {
		String content = "---\nname: reviewer\ndescription: Reviews code.\n# Reviewer";

		Optional<String> repaired = FrontMatterFixer.repair(content);

		assertTrue(repaired.isPresent());
		assertFalse(repaired.get().endsWith("\n"), repaired.get());
	}

	@Test
	void closesFrontMatterThatHasNoBody() {
		String content = """
				---
				name: reviewer
				description: Reviews code.
				""";

		Optional<String> repaired = FrontMatterFixer.repair(content);

		assertTrue(repaired.isPresent());
		assertEquals("---\nname: reviewer\ndescription: Reviews code.\n---\n", repaired.get());
	}
}
