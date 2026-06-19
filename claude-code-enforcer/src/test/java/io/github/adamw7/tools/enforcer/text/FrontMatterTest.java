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
}
