package io.github.adamw7.tools.adopt.step;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Random;
import java.util.stream.Collectors;

import org.apache.maven.enforcer.rule.api.EnforcerRuleException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import io.github.adamw7.tools.enforcer.doc.ClaudeMdFormatRule;

/**
 * Holds {@link ClaudeMdConformer} to the rule it exists to satisfy, by running
 * the real {@code claudeMdFormat} rule over its output instead of a copy of what
 * that rule is believed to want.
 * <p>
 * The conformer spells out the contract a second time — the title, the
 * {@code AGENTS.md} reference, the required sections, and the fence and comment
 * reading that decides which of them count. It has to, because the pipeline is a
 * plain library that must not ship an enforcer rule, so the constants cannot be
 * imported. What it must not do is drift: a rule that asks for one more section,
 * or reads a fence one notch differently, turns every adoption into a run that
 * conforms a document its own {@link VerifyStep} then rejects — on someone else's
 * repository, after the branch has been pushed.
 * <p>
 * Each case first asserts the raw fixture is rejected, so a conformer that
 * quietly stopped reshaping could not pass this test by doing nothing.
 *
 * @see ClaudeMdFormatRule#validating(java.io.File)
 */
class ClaudeMdConformerContractTest {

	/**
	 * The lines the two readers have to agree about: the required headings and the
	 * near-misses {@code claude init} writes instead, the two ways Markdown quotes
	 * code and the delimiters that open each, the list item an indent is measured
	 * from, the comment delimiters that make text inert, and the prose that has to
	 * survive all of it. A generated document is a sequence of these.
	 */
	private static final List<String> FRAGMENTS = List.of(
			"# CLAUDE.md",
			"# my-project",
			"## Project",
			"## Project purpose",
			"## Java version",
			"## Maven",
			"## Maven module structure",
			"## Principles for Java Development",
			"## Principles for Java development",
			"## Testing",
			"## Testing ##",
			"##  Testing",
			"## Dependencies",
			"## Overview",
			"### A sub-section",
			"#1 rule: run mvn install",
			"Some prose about the project.",
			"See [AGENTS.md](AGENTS.md) for the guide.",
			"An `AGENTS.md` is written beside it.",
			"",
			"```",
			"```java",
			"````",
			"~~~",
			"    ```",
			"    ## Testing",
			"    See [AGENTS.md](AGENTS.md) for the companion guide.",
			"    indented prose",
			"\t## Testing",
			"- the headings a generated file needs are:",
			"1. an ordered item",
			"    ## Deployment",
			"<!--",
			"-->",
			"<!-- ## Testing -->",
			"Superseded: <!--",
			"mvn install");

	/**
	 * How many generated documents the contract is checked over. Enough to walk the
	 * combinations the fixtures above were each found by, and few enough to stay well
	 * inside the per-test timeout — every one of them writes a file and runs the real
	 * rule over it.
	 */
	private static final int GENERATED_DOCUMENTS = 250;

	private final ClaudeMdConformer conformer = new ClaudeMdConformer();

	@TempDir
	private Path tempDir;

	@Test
	void renamesNearMissHeadingsIntoOnesTheRuleAccepts() {
		String generated = """
				# CLAUDE.md

				## Project purpose

				A security playground.

				## Java version

				Java 25.

				## Maven module structure

				Root pom is packaging=pom.

				## Principles for Java development

				SOLID.

				## Testing

				JUnit 5.

				## Dependencies

				Existing only.
				""";

		assertRuleRejects(generated);
		assertRuleAccepts(conformer.conform(generated));
	}

	@Test
	void appendsTheSectionsAGeneratedDocumentNeverHad() {
		String generated = """
				# CLAUDE.md

				## Overview

				A small command line utility.

				## Build

				Run `mvn install`.
				""";

		assertRuleRejects(generated);
		assertRuleAccepts(conformer.conform(generated));
	}

	@Test
	void conformsADocumentThatIsNothingButATitle() {
		String generated = "# my-project\n";

		assertRuleRejects(generated);
		assertRuleAccepts(conformer.conform(generated));
	}

	/**
	 * Both readers hold a heading inside a fence to be code, so the section is
	 * absent for the rule and has to be appended rather than counted.
	 */
	@Test
	void appendsASectionThatAppearsOnlyInsideACodeFence() {
		String generated = """
				# CLAUDE.md

				See [AGENTS.md](AGENTS.md) for the full guide.

				## Project

				A small command line utility.

				## Java version

				Java 25.

				## Maven

				Run `mvn install`.

				## Principles for Java Development

				SOLID.

				## Dependencies

				Existing only.

				A section skeleton looks like this:

				```markdown
				## Testing

				Write unit tests for all new logic.
				```
				""";

		assertRuleRejects(generated);
		assertRuleAccepts(conformer.conform(generated));
	}

	/** A section removed with an HTML comment is gone for both readers alike. */
	@Test
	void appendsASectionThatWasCommentedOut() {
		String generated = """
				# CLAUDE.md

				See [AGENTS.md](AGENTS.md) for the full guide.

				## Project

				A small command line utility.

				## Java version

				Java 25.

				## Maven

				Run `mvn install`.

				## Principles for Java Development

				SOLID.

				<!--
				## Testing

				Write unit tests for all new logic.
				-->

				## Dependencies

				Existing only.
				""";

		assertRuleRejects(generated);
		assertRuleAccepts(conformer.conform(generated));
	}

	/**
	 * An unterminated fence swallows every heading that follows it, so the rule
	 * reports those sections missing. Closing the fence keeps the swallowed lines
	 * code, which is what they read as — the sections come back because they are
	 * appended after it, not because the fence was reinterpreted.
	 */
	@Test
	void conformsADocumentLeftInsideAnOpenCodeFence() {
		String generated = """
				# CLAUDE.md

				See [AGENTS.md](AGENTS.md) for the full guide.

				## Project

				A small command line utility.

				## Java version

				Java 25.

				## Maven

				```bash
				mvn install

				## Principles for Java Development

				SOLID.

				## Testing

				JUnit 5.

				## Dependencies

				Existing only.
				""";

		assertRuleRejects(generated);
		assertRuleAccepts(conformer.conform(generated));
	}

	/**
	 * Markdown quotes code by indenting it four columns as readily as by fencing it,
	 * and the rule reads both as code. A conformer that only knew fences counted this
	 * sample's heading as the section the document already had, appended nothing, and
	 * left the rule demanding a section the document never carried.
	 */
	@Test
	void appendsASectionThatAppearsOnlyInsideAnIndentedCodeBlock() {
		String generated = """
				# CLAUDE.md

				See [AGENTS.md](AGENTS.md) for the full guide.

				## Project

				A small command line utility.

				## Java version

				Java 25.

				## Maven

				Run `mvn install`.

				## Principles for Java Development

				SOLID.

				## Dependencies

				Existing only.

				A section skeleton looks like this:

				    ## Testing

				    Write unit tests for all new logic.
				""";

		assertRuleRejects(generated);
		assertRuleAccepts(conformer.conform(generated));
	}

	/**
	 * The reference is held to the same reading as a heading: the rule's
	 * {@code containsInProse} looks only at the lines that carry structure, so a
	 * mention shown as an indented sample satisfies it no more than one inside a
	 * fence.
	 */
	@Test
	void addsTheReferenceWhenTheOnlyMentionIsInsideAnIndentedCodeBlock() {
		String generated = """
				# CLAUDE.md

				An example of the line to add:

				    See [AGENTS.md](AGENTS.md) for the guide.

				## Project

				A small command line utility.

				## Java version

				Java 25.

				## Maven

				Run `mvn install`.

				## Principles for Java Development

				SOLID.

				## Testing

				JUnit 5.

				## Dependencies

				Existing only.
				""";

		assertRuleRejects(generated);
		assertRuleAccepts(conformer.conform(generated));
	}

	/** A commented-out mention is inert for the rule, so the reference still has to be added. */
	@Test
	void addsTheReferenceWhenTheOnlyMentionIsInsideAnHtmlComment() {
		String generated = """
				# CLAUDE.md

				<!--
				See [AGENTS.md](AGENTS.md) for the full guide.
				-->

				## Project

				A small command line utility.

				## Java version

				Java 25.

				## Maven

				Run `mvn install`.

				## Principles for Java Development

				SOLID.

				## Testing

				JUnit 5.

				## Dependencies

				Existing only.
				""";

		assertRuleRejects(generated);
		assertRuleAccepts(conformer.conform(generated));
	}

	/**
	 * The rule ends a comment's reach by following every delimiter on the line, so a
	 * comment opened after text hides what comes below it. A conformer that only
	 * recognised a comment beginning at the start of a line read the hidden section as
	 * present and appended nothing.
	 */
	@Test
	void appendsASectionHiddenByACommentOpenedAfterText() {
		String generated = """
				# CLAUDE.md

				See [AGENTS.md](AGENTS.md) for the full guide.

				## Project

				A small command line utility.

				## Java version

				Java 25.

				## Maven

				Run `mvn install`.

				## Principles for Java Development

				SOLID.

				Superseded: <!--
				## Testing

				JUnit 5.
				-->

				## Dependencies

				Existing only.
				""";

		assertRuleRejects(generated);
		assertRuleAccepts(conformer.conform(generated));
	}

	/**
	 * An indented line directly below a paragraph is a lazy continuation of it rather
	 * than code, so the {@code ## Testing} written there is a heading both readers
	 * count — until a blank line appears above it, at which point it is an indented
	 * code block and neither counts it. Adding the {@code AGENTS.md} reference puts
	 * exactly that blank line there, so a reshape that settled its sections before
	 * adding the reference read the section as present, appended nothing, and then
	 * buried the heading it had been relying on. The adoption committed and pushed a
	 * {@code CLAUDE.md} its own {@link VerifyStep} reports as missing {@code ##
	 * Testing}.
	 */
	@Test
	void appendsASectionTheAgentsReferenceIsAboutToBury() {
		String generated = """
				# CLAUDE.md
				    ## Testing

				Some prose about the project.

				## Project

				A small command line utility.

				## Java version

				Java 25.

				## Maven

				Run `mvn install`.

				## Principles for Java Development

				SOLID.

				## Dependencies

				Existing only.
				""";

		String conformed = conformer.conform(generated);

		assertRuleRejects(generated);
		assertRuleAccepts(conformed);
		assertSettled(conformed);
	}

	/**
	 * A fence delimiter indented inside a list item is a delimiter, because the four
	 * columns that quote code are counted from the item's content rather than from the
	 * margin. Giving a bare section its stub puts a line at the margin, which ends the
	 * list and so lowers that column — and the delimiter stops being one. The
	 * terminator the reshape had already appended for the block it opened was then a
	 * delimiter <em>opening</em> a block, and the sections appended after it landed
	 * inside code, so the document came back still missing them.
	 */
	@Test
	void appendsASectionOutsideAFenceItsOwnStubClosed() {
		String generated = """
				# CLAUDE.md

				See [AGENTS.md](AGENTS.md) for the guide.

				## Project

				A small command line utility.

				## Java version

				Java 25.

				## Maven

				Run `mvn install`.

				## Principles for Java Development

				SOLID.

				- the headings a generated file needs are:
				    ## Testing
				    ## Deployment
				    ```
				""";

		String conformed = conformer.conform(generated);

		assertRuleRejects(generated);
		assertRuleAccepts(conformed);
		assertSettled(conformed);
	}

	/**
	 * The same reclassification, against the reference rather than a section: the only
	 * {@code AGENTS.md} mention sits inside a list item, where it is prose, and the
	 * stub that gives {@code ## Testing} a body ends that list and turns the mention
	 * into indented code. Whichever order the reshape settles the two in, one of them
	 * can be undone by the other, which is why it repeats until the document stops
	 * changing.
	 */
	@Test
	void addsTheReferenceItsOwnStubTurnedIntoCode() {
		String generated = """
				# CLAUDE.md

				## Project

				A small command line utility.

				## Java version

				Java 25.

				## Maven

				Run `mvn install`.

				## Principles for Java Development

				SOLID.

				## Dependencies

				Existing only.

				- the headings a generated file needs are:
				    ## Testing
				    ## Deployment

				    See [AGENTS.md](AGENTS.md) for the companion guide.
				""";

		String conformed = conformer.conform(generated);

		assertRuleRejects(generated);
		assertRuleAccepts(conformed);
		assertSettled(conformed);
	}

	/**
	 * A lone fence delimiter shown four columns in is how a document explains what a
	 * fence looks like, and the rule reads it as the indented code it is. A conformer
	 * that marked fences before indents opened a block on it that nothing closed, read
	 * every heading below as code, and appended its own closing delimiter — so the
	 * sections it then added landed after that delimiter, where the rule reads them as
	 * code too. The document came back still missing the sections the reshape had just
	 * written, which is the adoption failing its own {@link VerifyStep}.
	 */
	@Test
	void appendsSectionsOutsideAFenceShownInsideAnIndentedCodeBlock() {
		String generated = """
				# CLAUDE.md

				See [AGENTS.md](AGENTS.md) for the full guide.

				## Project

				A fenced block opens with a line like this:

				    ```

				and closes with the same run.
				""";

		assertRuleRejects(generated);
		assertRuleAccepts(conformer.conform(generated));
	}

	/**
	 * The same reading, on a document that already conforms. The reshape must be a
	 * no-op: the two-pass masking left a stray delimiter at the end of the file and
	 * appended a duplicate of every section it had read as code, and committed both.
	 */
	@Test
	void leavesADocumentCarryingAnIndentedFenceSampleUnchanged() {
		String conforming = """
				# CLAUDE.md

				See [AGENTS.md](AGENTS.md) for the full agent guide.

				## Project

				A small command line utility.

				## Java version

				Java 25.

				## Maven

				A fenced block opens with a line like this:

				    ```

				and closes with the same run.

				## Principles for Java Development

				SOLID.

				## Testing

				JUnit 5.

				## Dependencies

				Ask before adding a new one.
				""";

		assertRuleAccepts(conforming);
		assertEquals(conforming, conformer.conform(conforming));
	}

	/**
	 * A paragraph continuing a list item is prose to the rule, so a conformer that read
	 * it as code would append a section the document already carries.
	 */
	@Test
	void leavesADocumentWhoseSectionBodyContinuesAListItemUnchanged() {
		String conforming = """
				# CLAUDE.md

				See [AGENTS.md](AGENTS.md) for the full agent guide.

				## Project

				- the modules are:

				    `data`, `code`, and `adopt` are the reactor modules.

				## Java version

				Java 25.

				## Maven

				Run `mvn install`.

				## Principles for Java Development

				SOLID.

				## Testing

				JUnit 5.

				## Dependencies

				Ask before adding a new one.
				""";

		assertRuleAccepts(conforming);
		assertEquals(conforming, conformer.conform(conforming));
	}

	/**
	 * The rule strips a leading byte-order mark before reading; {@link String#strip()}
	 * does not, the mark being no kind of whitespace. So a conformer that did not strip
	 * one either read the title as absent and prepended a second — and the rule accepted
	 * that too, which is why only comparing the two readings catches it.
	 */
	@Test
	void leavesADocumentOpeningWithAByteOrderMarkAcceptableAndTitledOnce() {
		String conforming = """
				# CLAUDE.md

				See [AGENTS.md](AGENTS.md) for the full agent guide.

				## Project

				A small command line utility.

				## Java version

				Java 25.

				## Maven

				Run `mvn install`.

				## Principles for Java Development

				SOLID.

				## Testing

				JUnit 5.

				## Dependencies

				Ask before adding a new one.
				""";

		String conformed = conformer.conform("\uFEFF" + conforming);
		assertRuleAccepts(conformed);
		assertEquals(1, conformed.lines().filter("# CLAUDE.md"::equals).count(),
				"the rule reads the title through the mark, so the reshape must not add another:\n" + conformed);
	}

	@Test
	void leavesADocumentThatAlreadySatisfiesTheRuleAcceptable() {
		String conforming = """
				# CLAUDE.md

				See [AGENTS.md](AGENTS.md) for the full agent guide.

				## Project

				A small command line utility.

				## Java version

				Java 25.

				## Maven

				Run `mvn install`.

				## Principles for Java Development

				SOLID.

				## Testing

				JUnit 5.

				## Dependencies

				Ask before adding a new one.
				""";

		assertRuleAccepts(conforming);
		assertRuleAccepts(conformer.conform(conforming));
	}

	/**
	 * The same contract over documents nobody wrote by hand. Every case above is a
	 * shape the reshape was once wrong about, and each was found on a real repository
	 * rather than by reading the code: the two readings drift where a heading, a
	 * fence, an indent, a list item and a comment delimiter <em>meet</em>, and which
	 * combinations do that is not something a fixture list can be argued to cover.
	 * Building documents out of exactly those fragments walks the combinations
	 * instead.
	 * <p>
	 * The seeds are fixed, so a failure names a document that can be pasted back into
	 * a case of its own — which is what such a failure should become. Settling is
	 * asserted alongside acceptance because a reshape that keeps editing an accepted
	 * document puts a fresh diff in every pull request the repository ever gets.
	 */
	@Test
	void conformsGeneratedDocumentsBuiltFromTheFragmentsTheTwoReadersDisagreeOver() {
		for (int seed = 0; seed < GENERATED_DOCUMENTS; seed++) {
			String generated = documentFrom(seed);
			String conformed = conformer.conform(generated);
			assertRuleAccepts(conformed);
			assertEquals(conformed, conformer.conform(conformed),
					"seed " + seed + " does not settle; conformed from:\n" + generated);
		}
	}

	/**
	 * A document of between one and thirty {@link #FRAGMENTS}, drawn by a generator
	 * the seed alone decides, so the same seed is the same document on every run and
	 * on every machine.
	 */
	private static String documentFrom(int seed) {
		Random random = new Random(seed);
		return random.ints(1 + random.nextInt(30), 0, FRAGMENTS.size())
				.mapToObj(FRAGMENTS::get)
				.collect(Collectors.joining("\n", "", "\n"));
	}

	/**
	 * The reshape's own output has to be a document it then leaves alone. A re-adoption
	 * runs the reshape over the file the last one committed, so an output the reshape
	 * would edit again is a diff in every pull request that repository ever gets — and
	 * the pass that changes nothing is precisely the pass whose result the rule accepts.
	 */
	private void assertSettled(String conformed) {
		assertEquals(conformed, conformer.conform(conformed),
				"conforming an already conformed document must change nothing:\n" + conformed);
	}

	private void assertRuleAccepts(String content) {
		assertDoesNotThrow(ruleFor(content)::execute, "the conformed document must satisfy claudeMdFormat:\n" + content);
	}

	private void assertRuleRejects(String content) {
		assertThrows(EnforcerRuleException.class, ruleFor(content)::execute,
				"the fixture must start out failing claudeMdFormat, or it proves nothing:\n" + content);
	}

	private ClaudeMdFormatRule ruleFor(String content) {
		Path file = tempDir.resolve("CLAUDE.md");
		try {
			Files.writeString(file, content);
		} catch (IOException e) {
			throw new UncheckedIOException("Could not write " + file, e);
		}
		return ClaudeMdFormatRule.validating(file.toFile());
	}
}
