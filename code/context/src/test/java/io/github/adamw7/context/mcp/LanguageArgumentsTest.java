package io.github.adamw7.context.mcp;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Map;

import org.junit.jupiter.api.Test;

import io.github.adamw7.context.Language;

public class LanguageArgumentsTest {

	@Test
	void optionalLanguageFallsBackToDefault() {
		assertEquals(Language.JAVA, LanguageArguments.optionalLanguage(Map.of(), "language", Language.JAVA));
	}

	@Test
	void optionalLanguageResolvesTheGivenName() {
		assertEquals(Language.KOTLIN,
				LanguageArguments.optionalLanguage(Map.of("language", "kotlin"), "language", Language.JAVA));
	}
}
