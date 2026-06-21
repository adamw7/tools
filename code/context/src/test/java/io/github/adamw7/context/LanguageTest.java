package io.github.adamw7.context;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

public class LanguageTest {

	@Test
	void resolvesJavaIgnoringCase() {
		assertEquals(Language.JAVA, Language.fromName("java"));
		assertEquals(Language.JAVA, Language.fromName("JAVA"));
	}

	@Test
	void resolvesKotlinIgnoringSurroundingWhitespace() {
		assertEquals(Language.KOTLIN, Language.fromName("  Kotlin  "));
	}

	@Test
	void rejectsUnknownLanguage() {
		assertThrows(IllegalArgumentException.class, () -> Language.fromName("scala"));
	}
}
