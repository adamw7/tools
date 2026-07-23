package io.github.adamw7.tools.mcp;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.Map;

import org.junit.jupiter.api.Test;

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
	void requiredIntParsesNumericStrings() {
		assertEquals(3, ToolArguments.requiredInt(Map.of("columns_row", "3"), "columns_row"));
	}

	@Test
	void requiredIntParsesIntegers() {
		assertEquals(2, ToolArguments.requiredInt(Map.of("columns_row", 2), "columns_row"));
	}

	@Test
	void requiredIntFailsWhenMissing() {
		IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
				() -> ToolArguments.requiredInt(Map.of(), "columns_row"));
		assertEquals("Missing required argument: columns_row", error.getMessage());
	}

	@Test
	void requiredIntRejectsNonNumericValues() {
		IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
				() -> ToolArguments.requiredInt(Map.of("columns_row", "abc"), "columns_row"));
		assertEquals("Argument columns_row must be an integer but was abc", error.getMessage());
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
	void optionalBooleanFallsBackToDefault() {
		assertEquals(true, ToolArguments.optionalBoolean(Map.of(), "draft", true));
		assertEquals(false, ToolArguments.optionalBoolean(Map.of(), "draft", false));
	}

	@Test
	void optionalBooleanAcceptsRealBooleans() {
		assertEquals(true, ToolArguments.optionalBoolean(Map.of("draft", true), "draft", false));
		assertEquals(false, ToolArguments.optionalBoolean(Map.of("draft", false), "draft", true));
	}

	@Test
	void optionalBooleanParsesTextualBooleans() {
		assertEquals(true, ToolArguments.optionalBoolean(Map.of("draft", "true"), "draft", false));
		assertEquals(false, ToolArguments.optionalBoolean(Map.of("draft", "False"), "draft", true));
	}

	@Test
	void optionalBooleanRejectsNonBooleanValues() {
		IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
				() -> ToolArguments.optionalBoolean(Map.of("draft", "yes"), "draft", false));
		assertEquals("Argument draft must be a boolean but was yes", error.getMessage());
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
	void optionalIntRejectsNonNumericValues() {
		assertThrows(IllegalArgumentException.class,
				() -> ToolArguments.optionalInt(Map.of("depth", "abc"), "depth", 1));
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
}
