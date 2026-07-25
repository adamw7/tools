package io.github.adamw7.tools.adopt;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class TextTest {

	@Test
	void stripsARequiredValue() {
		assertEquals("value", Text.required("  value  ", "field"));
	}

	@Test
	void namesTheFieldWhenARequiredValueIsBlank() {
		IllegalArgumentException thrown = assertThrows(IllegalArgumentException.class,
				() -> Text.required("   ", "title"));
		assertEquals("title must not be blank", thrown.getMessage());
	}

	@Test
	void rejectsANullRequiredValue() {
		assertThrows(IllegalArgumentException.class, () -> Text.required(null, "branchName"));
	}

	@Test
	void treatsTextAsPresent() {
		assertTrue(Text.isPresent("value"));
	}

	/** Both entry points map an omitted argument to a blank string, so blank must count as absent. */
	@Test
	void treatsBlankAndNullAsAbsent() {
		assertFalse(Text.isPresent("   "));
		assertFalse(Text.isPresent(""));
		assertFalse(Text.isPresent(null));
	}
}
