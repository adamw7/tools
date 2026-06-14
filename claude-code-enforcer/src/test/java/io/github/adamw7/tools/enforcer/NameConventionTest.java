package io.github.adamw7.tools.enforcer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

class NameConventionTest {

	@Test
	void acceptsAKebabCaseNameThatMatches() {
		assertEquals(List.of(), collect("git-commit", "git-commit"));
	}

	@Test
	void rejectsAnEmptyNameOnce() {
		List<String> violations = collect("  ", "git-commit");

		assertEquals(1, violations.size());
		assertTrue(violations.get(0).contains("must not be empty"), violations.toString());
	}

	@Test
	void rejectsUpperCaseAndUnderscores() {
		assertTrue(collect("Git_Commit", "Git_Commit").stream().anyMatch(v -> v.contains("kebab-case")));
	}

	@Test
	void rejectsANameLongerThanTheMaximum() {
		String tooLong = "a".repeat(NameConvention.MAX_LENGTH + 1);

		assertTrue(collect(tooLong, tooLong).stream().anyMatch(v -> v.contains("exceeds")));
	}

	@Test
	void rejectsANameThatDoesNotMatchTheIdentifier() {
		assertTrue(collect("git-commit", "commit").stream().anyMatch(v -> v.contains("must match 'commit'")));
	}

	private List<String> collect(String name, String expected) {
		List<String> violations = new ArrayList<>();
		NameConvention.collect(name, expected, "where", violations);
		return violations;
	}
}
