package io.github.adamw7.tools.data.source.file;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.LinkedHashMap;
import java.util.Map;
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
	public void escapedBackslashBeforeClosingQuoteDoesNotSwallowSeparator() {
		// First field is the quoted value  "a\\"  (text ending in a backslash); the escaped
		// backslash must not make the closing quote look escaped, so the comma still splits.
		assertArrayEquals(new String[] { "\"a\\\\\"", "c" }, ToonSyntax.splitRow("\"a\\\\\",c"));
	}

	@Test
	public void escapedBackslashKeepsCommaInsideQuotesProtected() {
		// Value  "a\\,b"  contains an escaped backslash followed by a literal comma inside the
		// quotes, so the whole thing is a single field.
		assertArrayEquals(new String[] { "\"a\\\\,b\"", "c" }, ToonSyntax.splitRow("\"a\\\\,b\",c"));
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
	public void unquoteKeepsEscapedBackslashLiteralBeforeControlLetter() {
		// The raw value is  a \ \ n b : an escaped backslash followed by a literal
		// 'n', so it must decode to a backslash and an 'n', not to a newline.
		assertEquals("a\\nb", ToonSyntax.unquote("\"a\\\\nb\""));
		assertEquals("a\\tb", ToonSyntax.unquote("\"a\\\\tb\""));
	}

	@Test
	public void unquoteLeavesUnknownEscapeUntouched() {
		assertEquals("a\\xb", ToonSyntax.unquote("\"a\\xb\""));
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

	@Test
	public void topLevelSectionRecognisesKeyValueAtColumnZero() {
		assertTrue(ToonSyntax.isTopLevelSection(0, "name: value"));
	}

	@Test
	public void topLevelSectionRecognisesArrayHeaderAtColumnZero() {
		assertTrue(ToonSyntax.isTopLevelSection(0, "rows[2]{id,name}:"));
	}

	@Test
	public void indentedLineIsNotTopLevelSection() {
		assertFalse(ToonSyntax.isTopLevelSection(2, "name: value"));
	}

	@Test
	public void emptyLineIsNotTopLevelSection() {
		assertFalse(ToonSyntax.isTopLevelSection(0, ""));
	}

	@Test
	public void splitFieldsTrimsEachName() {
		assertArrayEquals(new String[] { "id", "name", "age" }, ToonSyntax.splitFields("id, name , age"));
	}

	@Test
	public void emitTabularHeaderEmitsCountThenFields() {
		Map<String, String> sink = new LinkedHashMap<>();
		ToonSyntax.emitTabularHeader(sink::put, "rows", 3, new String[] { "id", "name" });
		assertEquals("3", sink.get("rows"));
		assertEquals("id", sink.get("id"));
		assertEquals("name", sink.get("name"));
	}

	@Test
	public void emitTabularRowFlattensValuesAndIgnoresSurplus() {
		Map<String, String> sink = new LinkedHashMap<>();
		ToonSyntax.emitTabularRow(sink::put, "rows", new String[] { "id", "name" }, "7,\"Alice\",extra", 1);
		assertEquals("7", sink.get("rows[1].id"));
		assertEquals("Alice", sink.get("rows[1].name"));
		assertEquals(2, sink.size());
	}

	@Test
	public void emitInlineArrayEmitsLengthThenElements() {
		Map<String, String> sink = new LinkedHashMap<>();
		ToonSyntax.emitInlineArray(sink::put, "tags", "a, b, c");
		assertEquals("3", sink.get("tags"));
		assertEquals("a", sink.get("tags[0]"));
		assertEquals("b", sink.get("tags[1]"));
		assertEquals("c", sink.get("tags[2]"));
	}
}
