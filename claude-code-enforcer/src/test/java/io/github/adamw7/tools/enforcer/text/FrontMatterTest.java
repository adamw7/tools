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

	/**
	 * Only a quote that opens the value quotes it. Reading the apostrophe as an
	 * opening quote left a scalar that never closed, and the trailing note was kept
	 * as part of the description Claude Code never sees it in.
	 */
	@Test
	void dropsATrailingCommentAfterAnApostropheInAPlainScalar() {
		Optional<FrontMatter> frontMatter = FrontMatter.parse("---\ndescription: Don't stop # a note\n---\n");

		assertEquals(Optional.of("Don't stop"), frontMatter.orElseThrow().value("description"));
	}

	@Test
	void dropsATrailingCommentAfterADoubleQuoteInAPlainScalar() {
		Optional<FrontMatter> frontMatter = FrontMatter.parse("---\nname: say \"hi\" now # a note\n---\n");

		assertEquals(Optional.of("say \"hi\" now"), frontMatter.orElseThrow().value("name"));
	}

	@Test
	void dropsATrailingCommentAfterASingleQuotedScalarWithAnEscapedQuote() {
		Optional<FrontMatter> frontMatter = FrontMatter.parse("---\ndescription: 'it''s here' # a note\n---\n");

		assertEquals(Optional.of("it's here"), frontMatter.orElseThrow().value("description"));
	}

	/**
	 * A block no YAML loader can read is no front matter: Claude Code's loader fails
	 * on it exactly as this one does, so there is nothing here to validate. The rules
	 * report its absence, which says more than a guess at the malformed line could.
	 */
	@Test
	void readsNoFrontMatterFromABlockYamlCannotRead() {
		assertTrue(FrontMatter.parse("---\ndescription: \"a\" and \"b\"\n---\n").isEmpty());
		assertTrue(FrontMatter.parse("---\ndescription: \"oops # a note\n---\n").isEmpty());
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

	/**
	 * A double-quoted scalar is the one style that carries escapes, and a YAML
	 * loader resolves them. Reading the two characters {@code \n} literally counted
	 * a description Claude Code never sees and could fail a name over a backslash
	 * that is not in it.
	 */
	@Test
	void resolvesTheEscapeSequencesOfADoubleQuotedScalar() {
		Optional<FrontMatter> frontMatter = FrontMatter.parse("---\ndescription: \"one\\ntwo\"\n---\n");

		assertEquals(Optional.of("one two"), frontMatter.orElseThrow().value("description"));
	}

	/** A mapping written inline is the same mapping as one written on the lines below its key. */
	@Test
	void foldsAMappingWrittenInFlowStyle() {
		Optional<FrontMatter> frontMatter = FrontMatter
				.parse("---\ngenerated: {by: agent/1, at: 2026-01-01}\n---\n");

		assertEquals(Optional.of("by: agent/1 at: 2026-01-01"), frontMatter.orElseThrow().value("generated"));
	}

	/**
	 * The block is composed rather than loaded, so a value stays the text its author
	 * wrote. Constructing it into a Java object would answer {@code 0.2} for a
	 * version declared as {@code 0.20} — and the OKF rule compares that string
	 * against the version it demands.
	 */
	@Test
	void answersAVersionAsTheTextItWasWrittenAs() {
		FrontMatter frontMatter = FrontMatter.parse("---\nokf_version: 0.20\n---\n").orElseThrow();

		assertEquals(Optional.of("0.20"), frontMatter.value("okf_version"));
	}

	/**
	 * A block that reads but is not a mapping is present and declares nothing, which
	 * is not the same as one no loader can read at all.
	 */
	@Test
	void readsABlockThatIsNotAMappingAsDeclaringNothing() {
		FrontMatter frontMatter = FrontMatter.parse("---\nname:git-commit\n---\n").orElseThrow();

		assertEquals(List.of(), frontMatter.keys());
	}

	/**
	 * An alias is the node it names, not a copy of it, so a handful of nested
	 * aliases describe a value of hundreds of millions of characters. Rendering one
	 * stops at the cap instead of expanding it, which is what keeps a rule reading a
	 * repository's files from being the thing that runs out of memory.
	 */
	@Test
	void stopsRenderingAnAliasThatExpandsWithoutBound() {
		FrontMatter frontMatter = FrontMatter.parse("""
				---
				a: &a [x, x, x, x, x, x, x, x, x]
				b: &b [*a, *a, *a, *a, *a, *a, *a, *a, *a]
				c: &c [*b, *b, *b, *b, *b, *b, *b, *b, *b]
				d: &d [*c, *c, *c, *c, *c, *c, *c, *c, *c]
				e: &e [*d, *d, *d, *d, *d, *d, *d, *d, *d]
				f: [*e, *e, *e, *e, *e, *e, *e, *e, *e]
				---
				""").orElseThrow();

		assertTrue(frontMatter.value("f").orElseThrow().length() <= 8192, "the expansion must be capped");
	}

	/**
	 * An anchor may name a node that contains it, which composes to a graph with a
	 * cycle rather than a tree. The walk of such a graph never reaches a leaf to append
	 * at, so the length cap alone never came into play and it recursed until the stack
	 * ran out — and a {@link StackOverflowError} is not the {@code YAMLException} the
	 * parse catches, so it failed the build as an internal error instead of being read
	 * as the front matter it is.
	 */
	@Test
	void readsAnAnchorThatContainsItselfWithoutExhaustingTheStack() {
		FrontMatter frontMatter = FrontMatter.parse("""
				---
				type: concept
				description: &loop
				  - *loop
				---
				""").orElseThrow();

		assertEquals(List.of("type", "description"), frontMatter.keys());
		assertTrue(frontMatter.value("description").orElseThrow().length() <= 8192, "the walk must be capped");
	}

	/** A mapping can name itself the same way a sequence can. */
	@Test
	void readsAMappingThatContainsItselfWithoutExhaustingTheStack() {
		FrontMatter frontMatter = FrontMatter.parse("""
				---
				name: skill
				generated: &loop
				  by: *loop
				---
				""").orElseThrow();

		assertEquals(List.of("name", "generated"), frontMatter.keys());
		assertTrue(frontMatter.value("generated").orElseThrow().length() <= 8192, "the walk must be capped");
	}

}
