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
}
