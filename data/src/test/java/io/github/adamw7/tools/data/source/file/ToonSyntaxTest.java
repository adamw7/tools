package io.github.adamw7.tools.data.source.file;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.regex.Matcher;

import org.junit.jupiter.api.Test;

public class ToonSyntaxTest {

	@Test
	public void noIndentation() {
		assertEquals(0, ToonSyntax.indentationOf("name: value"));
	}

	@Test
	public void countsLeadingSpaces() {
		assertEquals(2, ToonSyntax.indentationOf("  name: value"));
	}

	@Test
	public void tabsCountAsTwo() {
		assertEquals(4, ToonSyntax.indentationOf("\t\tname"));
	}

	@Test
	public void mixesSpacesAndTabs() {
		assertEquals(3, ToonSyntax.indentationOf(" \tname"));
	}

	@Test
	public void stopsCountingAtFirstNonWhitespace() {
		assertEquals(2, ToonSyntax.indentationOf("  a  b"));
	}

	@Test
	public void splitsPlainValues() {
		assertArrayEquals(new String[] { "a", "b", "c" }, ToonSyntax.splitRow("a,b,c"));
	}

	@Test
	public void keepsCommaInsideQuotes() {
		assertArrayEquals(new String[] { "\"a,b\"", "c" }, ToonSyntax.splitRow("\"a,b\",c"));
	}

	@Test
	public void escapedQuoteDoesNotToggleQuoting() {
		assertArrayEquals(new String[] { "\"a\\\"b\"", "c" }, ToonSyntax.splitRow("\"a\\\"b\",c"));
	}

	@Test
	public void leadingCommaProducesEmptyValue() {
		assertArrayEquals(new String[] { "", "a" }, ToonSyntax.splitRow(",a"));
	}

	@Test
	public void trailingCommaDropsTrailingEmptyValue() {
		assertArrayEquals(new String[] { "a" }, ToonSyntax.splitRow("a,"));
	}

	@Test
	public void splitEmptyRowYieldsNoValues() {
		assertEquals(0, ToonSyntax.splitRow("").length);
	}

	@Test
	public void unquoteReturnsNullForNull() {
		assertNull(ToonSyntax.unquote(null));
	}

	@Test
	public void unquoteReturnsEmptyForEmpty() {
		assertEquals("", ToonSyntax.unquote(""));
	}

	@Test
	public void unquoteTrimsUnquotedValue() {
		assertEquals("abc", ToonSyntax.unquote("  abc  "));
	}

	@Test
	public void unquoteStripsSurroundingQuotes() {
		assertEquals("abc", ToonSyntax.unquote("\"abc\""));
	}

	@Test
	public void unquoteTrimsBeforeStrippingQuotes() {
		assertEquals("abc", ToonSyntax.unquote("  \"abc\"  "));
	}

	@Test
	public void singleQuoteIsNotTreatedAsQuoted() {
		assertEquals("\"", ToonSyntax.unquote("\""));
	}

	@Test
	public void unquoteResolvesEscapeSequences() {
		assertEquals("a\tb", ToonSyntax.unquote("\"a\\tb\""));
		assertEquals("a\"b", ToonSyntax.unquote("\"a\\\"b\""));
		assertEquals("a\\b", ToonSyntax.unquote("\"a\\\\b\""));
		assertEquals("line\nbreak", ToonSyntax.unquote("\"line\\nbreak\""));
	}

	@Test
	public void keyValuePatternMatchesDottedKeys() {
		Matcher matcher = ToonSyntax.KEY_VALUE_PATTERN.matcher("user.name: Alice");
		assertTrue(matcher.matches());
		assertEquals("user.name", matcher.group(1));
		assertEquals("Alice", matcher.group(2));
	}

	@Test
	public void arrayHeaderPatternCapturesSizeAndFields() {
		Matcher matcher = ToonSyntax.ARRAY_HEADER_PATTERN.matcher("rows[2]{id,name}: ");
		assertTrue(matcher.matches());
		assertEquals("rows", matcher.group(1));
		assertEquals("2", matcher.group(2));
		assertEquals("id,name", matcher.group(4));
	}
}
