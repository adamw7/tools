package io.github.adamw7.tools.enforcer.text;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
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
	void dropsBlankLinesBeforeTheOpeningDelimiter() {
		String content = "\n\n---\nname: reviewer\ndescription: Reviews code.\n---\nbody\n";

		Optional<String> repaired = FrontMatterFixer.repair(content);

		assertTrue(repaired.isPresent());
		assertTrue(repaired.get().startsWith("---\n"), repaired.get());
		assertTrue(FrontMatter.parse(repaired.get()).isPresent(), repaired.get());
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
	void doesNotMistakeAThematicBreakFollowedByProseContainingAColonForFrontMatter() {
		String content = """
				---

				Some notes: here is what the colon is doing.
				More prose.
				""";

		assertTrue(FrontMatterFixer.repair(content).isEmpty(), () -> FrontMatterFixer.repair(content).toString());
	}

	@Test
	void doesNotSwallowBodyProseContainingAColonIntoAnUnclosedBlock() {
		String content = """
				---
				name: reviewer

				Use this skill when: you need a review.
				""";

		Optional<String> repaired = FrontMatterFixer.repair(content);

		assertTrue(repaired.isPresent());
		assertEquals("---\nname: reviewer\n\n---\nUse this skill when: you need a review.\n", repaired.get());
		assertEquals(Optional.of("reviewer"), FrontMatter.parse(repaired.get()).flatMap(fm -> fm.value("name")));
		assertEquals(List.of("name"), FrontMatter.parse(repaired.get()).orElseThrow().keys());
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

	@Test
	void keepsCarriageReturnLineFeedSeparatorsWhenRepairing() {
		String content = "---\r\nname: reviewer\r\nBody one.\r\nBody two.\r\n";

		assertEquals(Optional.of("---\r\nname: reviewer\r\n---\r\nBody one.\r\nBody two.\r\n"),
				FrontMatterFixer.repair(content));
	}

	@Test
	void keepsLineFeedSeparatorsWhenRepairing() {
		String content = "---\nname: reviewer\nBody one.\n";

		assertEquals(Optional.of("---\nname: reviewer\n---\nBody one.\n"), FrontMatterFixer.repair(content));
	}

	/**
	 * Malformed YAML inside delimiters that are already canonical is beyond this
	 * fixer. {@link FrontMatter} declines to read such a block, so the repair is
	 * attempted and changes nothing — and reporting that as a repair would have the
	 * caller rewrite the file it already had, and log a fix, on every build.
	 */
	@Test
	void reportsNoRepairForABlockItCannotMend() {
		assertTrue(FrontMatterFixer.repair("---\ndescription: \"a\" and \"b\"\n---\nBody.\n").isEmpty());
	}
}
