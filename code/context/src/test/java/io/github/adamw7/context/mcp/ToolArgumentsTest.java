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
	void optionalBoundedIntAcceptsValuesWithinRange() {
		assertEquals(5, ToolArguments.optionalBoundedInt(Map.of("depth", 5), "depth", 1, 0, 10));
	}

	@Test
	void optionalBoundedIntFallsBackToDefaultWhenMissing() {
		assertEquals(1, ToolArguments.optionalBoundedInt(Map.of(), "depth", 1, 0, 10));
	}

	@Test
	void optionalBoundedIntRejectsValuesAboveTheMaximum() {
		IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
				() -> ToolArguments.optionalBoundedInt(Map.of("depth", 11), "depth", 1, 0, 10));
		assertEquals("Argument depth must be between 0 and 10 but was 11", error.getMessage());
	}

	@Test
	void optionalBoundedIntRejectsNegativeValues() {
		assertThrows(IllegalArgumentException.class,
				() -> ToolArguments.optionalBoundedInt(Map.of("depth", -1), "depth", 1, 0, 10));
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
