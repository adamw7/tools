package io.github.adamw7.tools.adopt.step;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;

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
