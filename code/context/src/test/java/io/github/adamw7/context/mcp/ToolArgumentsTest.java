package io.github.adamw7.context.mcp;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.Map;

import org.junit.jupiter.api.Test;

import io.github.adamw7.context.Language;

public class ToolArgumentsTest {

	@Test
	void requiredStringReturnsTheValue() {
		assertEquals("foo", ToolArguments.requiredString(Map.of("path", "foo"), "path"));
	}

	@Test
	void requiredStringFailsWhenMissing() {
		IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
				() -> ToolArguments.requiredString(Map.of(), "path"));
		assertEquals("Missing required argument: path", error.getMessage());
	}

	@Test
	void optionalStringFallsBackToDefault() {
		assertEquals("json", ToolArguments.optionalString(Map.of(), "format", "json"));
	}

	@Test
	void optionalStringPrefersTheGivenValue() {
		assertEquals("markdown", ToolArguments.optionalString(Map.of("format", "markdown"), "format", "json"));
	}

	@Test
	void optionalIntFallsBackToDefault() {
		assertEquals(1, ToolArguments.optionalInt(Map.of(), "depth", 1));
	}

	@Test
	void optionalIntParsesNumericStrings() {
		assertEquals(3, ToolArguments.optionalInt(Map.of("depth", "3"), "depth", 1));
	}

	@Test
	void optionalIntParsesIntegers() {
		assertEquals(2, ToolArguments.optionalInt(Map.of("depth", 2), "depth", 1));
	}

	@Test
	void optionalLanguageFallsBackToDefault() {
		assertEquals(Language.JAVA, ToolArguments.optionalLanguage(Map.of(), "language", Language.JAVA));
	}

	@Test
	void optionalLanguageResolvesTheGivenName() {
		assertEquals(Language.KOTLIN, ToolArguments.optionalLanguage(Map.of("language", "kotlin"), "language",
				Language.JAVA));
	}
}
