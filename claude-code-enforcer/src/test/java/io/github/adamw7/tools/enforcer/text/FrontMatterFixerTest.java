package io.github.adamw7.tools.enforcer.text;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Optional;
import java.util.Random;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;

class FrontMatterFixerTest {

	/**
	 * The lines a generated document is built from: the delimiters this fixer mends,
	 * the entries that make a region front matter, the values and indentation no
	 * loader can read, and the prose a block has to stop at.
	 */
	private static final List<String> FRAGMENTS = List.of(
			"---",
			"----",
			"-----",
			"",
			"   ",
			"name: reviewer",
			"description: Reviews code.",
			"description: \"a\" and \"b\"",
			"allowed-tools: Bash",
			"a: b: c",
			"key:",
			"\tname: tabbed",
			"  nested: y",
			"- item",
			"# Reviewer",
			"Some prose.",
			"Note: prose with a colon");

	/**
	 * How many generated documents the contract is checked over — enough to reach the
	 * combinations the fixtures below were each found by, and few enough that the whole
	 * class still runs in a fraction of the per-test timeout.
	 */
	private static final int GENERATED_DOCUMENTS = 2000;

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
	 * fixer. {@link FrontMatter} declines to read such a block, and reporting a repair
	 * for it would have the caller rewrite the file it already had, and log a fix, on
	 * every build.
	 */
	@Test
	void reportsNoRepairForABlockItCannotMend() {
		assertTrue(FrontMatterFixer.repair("---\ndescription: \"a\" and \"b\"\n---\nBody.\n").isEmpty());
	}

	/**
	 * The same unreadable block, this time behind delimiters the fixer <em>can</em>
	 * mend. Writing the canonical delimiters around it changed the file without
	 * changing what it declares: the block was as unreadable afterwards as before, so
	 * the caller rewrote the author's file, logged that it had auto-fixed the front
	 * matter, and then failed the same run on the same file for having none.
	 */
	@Test
	void reportsNoRepairWhenMendingTheDelimitersLeavesTheBlockUnreadable() {
		String content = """
				----
				name: reviewer
				description: "a" and "b"
				----
				# Reviewer
				""";

		assertEquals(Optional.empty(), FrontMatterFixer.repair(content));
	}

	/** The same, for the block whose closing delimiter is missing rather than over-dashed. */
	@Test
	void reportsNoRepairWhenClosingTheBlockLeavesItUnreadable() {
		String content = """
				---
				name: reviewer
				description: "a" and "b"
				# Reviewer
				""";

		assertEquals(Optional.empty(), FrontMatterFixer.repair(content));
	}

	/**
	 * A tab is not YAML indentation, so a block indented with one is unreadable for a
	 * reason no delimiter carries either — and one written past leading blank lines
	 * has all three of this fixer's repairs to make before that shows.
	 */
	@Test
	void reportsNoRepairWhenTheBlockItWouldMoveToLineOneIsUnreadable() {
		String content = """

				----
				\tname: reviewer
				description: Reviews code.
				""";

		assertEquals(Optional.empty(), FrontMatterFixer.repair(content));
	}

	/**
	 * The contract itself, over documents built from the lines that make a block
	 * unreadable and the delimiters that make one mendable — because what a caller
	 * relies on is not the three fixtures above but the promise they are examples of:
	 * a reported repair is one the caller may write to the author's file and one the
	 * checks below it can then read. The fixtures were each found this way, and are
	 * kept as fixtures so a failure names the document rather than a seed.
	 * <p>
	 * The generator draws from a fixed seed sequence, so the same run happens on every
	 * machine, and the documents are only parsed rather than written anywhere, which is
	 * what keeps a few thousand of them inside the per-test timeout.
	 */
	@Test
	void everyRepairItReportsIsOneThatParsesAndChangesTheDocument() {
		for (int seed = 0; seed < GENERATED_DOCUMENTS; seed++) {
			String generated = documentFrom(seed);
			Optional<String> repaired = FrontMatterFixer.repair(generated);
			repaired.ifPresent(fixed -> {
				assertTrue(FrontMatter.parse(fixed).isPresent(), "a reported repair must parse:\n" + fixed);
				assertNotEquals(generated, fixed, "a repair that changes nothing is none");
			});
		}
	}

	/** A document of between one and ten {@link #FRAGMENTS}, decided by {@code seed} alone. */
	private static String documentFrom(int seed) {
		Random random = new Random(seed);
		return random.ints(1 + random.nextInt(10), 0, FRAGMENTS.size())
				.mapToObj(FRAGMENTS::get)
				.collect(Collectors.joining("\n", "", "\n"));
	}
}
