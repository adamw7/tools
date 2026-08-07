package io.github.adamw7.tools.enforcer.text;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;

class FrontMatterTest {

	private static final String DOCUMENT = """
			---
			name: git-commit
			description: Generate commit messages.
			model: claude-opus-4-8
			---
			# Body
			""";

	@Test
	void parsesADelimitedBlock() {
		Optional<FrontMatter> frontMatter = FrontMatter.parse(DOCUMENT);

		assertTrue(frontMatter.isPresent());
	}

	@Test
	void returnsEmptyWhenNoFrontMatter() {
		assertTrue(FrontMatter.parse("# Just a heading").isEmpty());
	}

	@Test
	void returnsEmptyWhenBlockIsNotClosed() {
		assertTrue(FrontMatter.parse("---\nname: x\n# unterminated").isEmpty());
	}

	@Test
	void requiresTheOpeningDelimiterOnTheFirstLine() {
		// Claude Code only recognises front matter that starts on line one, so a
		// block reached after blank lines must not parse here either.
		assertTrue(FrontMatter.parse("\n\n---\nname: x\n---\n").isEmpty());
	}

	@Test
	void detectsDeclaredKeys() {
		FrontMatter frontMatter = FrontMatter.parse(DOCUMENT).orElseThrow();

		assertTrue(frontMatter.hasKey("name"));
		assertFalse(frontMatter.hasKey("tools"));
	}

	@Test
	void doesNotTreatAKeyPrefixAsAMatch() {
		FrontMatter frontMatter = FrontMatter.parse("---\nnamed: x\n---\n").orElseThrow();

		assertFalse(frontMatter.hasKey("name"));
	}

	@Test
	void readsValues() {
		FrontMatter frontMatter = FrontMatter.parse(DOCUMENT).orElseThrow();

		assertEquals(Optional.of("git-commit"), frontMatter.value("name"));
		assertEquals(Optional.of("Generate commit messages."), frontMatter.value("description"));
		assertEquals(Optional.empty(), frontMatter.value("tools"));
	}

	@Test
	void treatsAValuelessKeyAsAnEmptyValue() {
		FrontMatter frontMatter = FrontMatter.parse("---\nname:\n---\n").orElseThrow();

		assertEquals(Optional.of(""), frontMatter.value("name"));
	}

	@Test
	void listsKeysInOrder() {
		FrontMatter frontMatter = FrontMatter.parse(DOCUMENT).orElseThrow();

		assertEquals(List.of("name", "description", "model"), frontMatter.keys());
	}

	@Test
	void keysAgreeWithHasKeyWhenColonHasNoSeparatingSpace() {
		FrontMatter frontMatter = FrontMatter.parse("---\nname:git-commit\n---\n").orElseThrow();

		// A YAML mapping needs a space after the colon, so "name:git-commit" declares
		// no key. keys(), hasKey() and value() must all agree on that.
		assertEquals(List.of(), frontMatter.keys());
		assertFalse(frontMatter.hasKey("name"));
		assertEquals(Optional.empty(), frontMatter.value("name"));
	}

	@Test
	void ignoresCommentsAndListItemsWhenListingKeys() {
		FrontMatter frontMatter = FrontMatter.parse("""
				---
				name: x
				# a comment
				tools:
				  - Read
				---
				""").orElseThrow();

		assertEquals(List.of("name", "tools"), frontMatter.keys());
	}

	@Test
	void doesNotReadAKeyOutOfAnIndentedContinuationLine() {
		FrontMatter frontMatter = FrontMatter.parse("""
				---
				name: demo
				description: >
				  Use this when: the user asks for a demo.
				---
				""").orElseThrow();

		// The wrapped prose continues the description; "Use this when" is not a key an
		// author declared, and reporting it would fail the build for an unknown key.
		assertEquals(List.of("name", "description"), frontMatter.keys());
		assertFalse(frontMatter.hasKey("Use this when"));
	}

	@Test
	void foldsAFoldedBlockScalarIntoItsValue() {
		FrontMatter frontMatter = FrontMatter.parse("""
				---
				name: demo
				description: >
				  Generate a demo report.

				  Use when the user asks for one.
				---
				""").orElseThrow();

		assertEquals(Optional.of("Generate a demo report. Use when the user asks for one."),
				frontMatter.value("description"));
	}

	@Test
	void foldsALiteralBlockScalarWithChompingIndicator() {
		FrontMatter frontMatter = FrontMatter.parse("""
				---
				description: |-
				  First line.
				  Second line.
				name: demo
				---
				""").orElseThrow();

		assertEquals(Optional.of("First line. Second line."), frontMatter.value("description"));
		assertEquals(Optional.of("demo"), frontMatter.value("name"));
	}

	@Test
	void treatsAnEmptyBlockScalarAsAnEmptyValue() {
		FrontMatter frontMatter = FrontMatter.parse("---\ndescription: >\nname: demo\n---\n").orElseThrow();

		assertEquals(Optional.of(""), frontMatter.value("description"));
	}

	@Test
	void doesNotMistakeAPipeCharacterInProseForABlockScalar() {
		FrontMatter frontMatter = FrontMatter.parse("---\ndescription: a | b\n---\n").orElseThrow();

		assertEquals(Optional.of("a | b"), frontMatter.value("description"));
	}

	@Test
	void readsADoubleQuotedValueWithoutItsQuotes() {
		FrontMatter frontMatter = FrontMatter.parse("---\nname: \"git-commit\"\n---\n").orElseThrow();

		assertEquals(Optional.of("git-commit"), frontMatter.value("name"));
	}

	@Test
	void readsASingleQuotedValueWithoutItsQuotes() {
		FrontMatter frontMatter = FrontMatter.parse("---\nname: 'git-commit'\n---\n").orElseThrow();

		assertEquals(Optional.of("git-commit"), frontMatter.value("name"));
	}

	/** A description carrying a {@code : } has to be quoted to parse as YAML at all. */
	@Test
	void readsAQuotedDescriptionCarryingAColon() {
		FrontMatter frontMatter = FrontMatter.parse("---\ndescription: \"Use when: committing.\"\n---\n")
				.orElseThrow();

		assertEquals(Optional.of("Use when: committing."), frontMatter.value("description"));
	}

	@Test
	void resolvesTheEscapeOfEachQuotingStyle() {
		assertEquals(Optional.of("a \" b"),
				FrontMatter.parse("---\ndescription: \"a \\\" b\"\n---\n").orElseThrow().value("description"));
		assertEquals(Optional.of("it's"),
				FrontMatter.parse("---\ndescription: 'it''s'\n---\n").orElseThrow().value("description"));
	}

	/** The outer quotes of {@code "a" and "b"} are two pairs, not one wrapping the value. */
	@Test
	void keepsQuotesThatDoNotWrapTheWholeValue() {
		FrontMatter frontMatter = FrontMatter.parse("---\ndescription: \"a\" and \"b\"\n---\n").orElseThrow();

		assertEquals(Optional.of("\"a\" and \"b\""), frontMatter.value("description"));
	}

	@Test
	void readsAnEmptyQuotedValueAsBlank() {
		FrontMatter frontMatter = FrontMatter.parse("---\ndescription: \"\"\n---\n").orElseThrow();

		assertEquals(Optional.of(""), frontMatter.value("description"));
	}

	/**
	 * A YAML loader keeps the last declaration of a repeated key, so that is the
	 * value Claude Code acts on. Reading the first validated a value the tool never
	 * sees.
	 */
	@Test
	void readsTheLastDeclarationOfARepeatedKey() {
		FrontMatter frontMatter = FrontMatter.parse("---\ndescription: first\ndescription: second\n---\n")
				.orElseThrow();

		assertEquals(Optional.of("second"), frontMatter.value("description"));
	}

	@Test
	void reportsEachKeyDeclaredMoreThanOnce() {
		FrontMatter frontMatter = FrontMatter
				.parse("---\nname: a\ndescription: one\ndescription: two\nname: b\n---\n").orElseThrow();

		assertEquals(List.of("description", "name"), frontMatter.duplicateKeys());
	}

	@Test
	void namesARepeatedKeyOnceHoweverOftenItIsDeclared() {
		FrontMatter frontMatter = FrontMatter.parse("---\nmodel: a\nmodel: b\nmodel: c\n---\n").orElseThrow();

		assertEquals(List.of("model"), frontMatter.duplicateKeys());
	}

	@Test
	void reportsNoDuplicateWhenEveryKeyIsDeclaredOnce() {
		FrontMatter frontMatter = FrontMatter.parse("---\nname: a\ndescription: d\n---\n").orElseThrow();

		assertEquals(List.of(), frontMatter.duplicateKeys());
	}

	@Test
	void dropsATrailingCommentFromAPlainScalar() {
		Optional<FrontMatter> frontMatter = FrontMatter.parse("---\nname: git-commit # the commit helper\n---\n");

		assertEquals(Optional.of("git-commit"), frontMatter.orElseThrow().value("name"));
	}

	@Test
	void dropsATrailingCommentAfterAQuotedScalar() {
		Optional<FrontMatter> frontMatter = FrontMatter.parse("---\ndescription: \"a: b\" # note\n---\n");

		assertEquals(Optional.of("a: b"), frontMatter.orElseThrow().value("description"));
	}

	/** Only whitespace opens a comment, so a hash inside a value stays part of it. */
	@Test
	void keepsAHashThatDoesNotFollowWhitespace() {
		Optional<FrontMatter> frontMatter = FrontMatter.parse("---\nname: issue#7\n---\n");

		assertEquals(Optional.of("issue#7"), frontMatter.orElseThrow().value("name"));
	}

	@Test
	void keepsAHashInsideQuotes() {
		Optional<FrontMatter> frontMatter = FrontMatter.parse("---\ndescription: \"tag # here\"\n---\n");

		assertEquals(Optional.of("tag # here"), frontMatter.orElseThrow().value("description"));
	}

	@Test
	void readsAValueThatIsNothingButACommentAsEmpty() {
		Optional<FrontMatter> frontMatter = FrontMatter.parse("---\nname: # to be decided\n---\n");

		assertEquals(Optional.of(""), frontMatter.orElseThrow().value("name"));
	}

	@Test
	void foldsABlockScalarAnnouncedWithATrailingComment() {
		Optional<FrontMatter> frontMatter = FrontMatter.parse("---\ndescription: > # folded\n  one\n  two\n---\n");

		assertEquals(Optional.of("one two"), frontMatter.orElseThrow().value("description"));
	}

	/**
	 * A nested mapping leaves the key's own line empty, and reading it literally
	 * answered the empty string for a value written out in full on the lines below —
	 * so a rule looking for something in it reported it missing while it was there.
	 */
	@Test
	void foldsAMappingWrittenOnTheLinesBelowItsKey() {
		Optional<FrontMatter> frontMatter = FrontMatter
				.parse("---\ngenerated:\n  by: agent/1\n  at: 2026-01-01\n---\n");

		assertEquals(Optional.of("by: agent/1 at: 2026-01-01"), frontMatter.orElseThrow().value("generated"));
	}

	/** YAML continues a plain scalar on the indented lines below it, and so does this. */
	@Test
	void foldsAPlainScalarWrappedOntoTheNextLine() {
		Optional<FrontMatter> frontMatter = FrontMatter.parse("---\ndescription:\n  one\n  two\n---\n");

		assertEquals(Optional.of("one two"), frontMatter.orElseThrow().value("description"));
	}

	/** A key with nothing below it still declares nothing, which is what the format rules report. */
	@Test
	void readsABareKeyWithNothingBelowItAsEmpty() {
		Optional<FrontMatter> frontMatter = FrontMatter.parse("---\nname:\ndescription: d\n---\n");

		assertEquals(Optional.of(""), frontMatter.orElseThrow().value("name"));
	}
}
