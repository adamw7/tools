package io.github.adamw7.tools.adopt.step;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

import io.github.adamw7.tools.adopt.AdoptionException;
import io.github.adamw7.tools.adopt.step.XmlElementSpans.Span;

class XmlElementSpansTest {

	@Test
	void spansAnElementFromItsStartTagToItsEndTag() {
		String xml = "<a>text</a>";
		Span span = only(xml);
		assertEquals(0, span.tagStart());
		assertEquals(3, span.contentStart(), "content must start just past the start tag's '>'");
		assertEquals(7, span.contentEnd(), "content must end at the '<' opening the end tag");
		assertFalse(span.selfClosing());
	}

	@Test
	void marksAnEmptyElementAsSelfClosing() {
		Span span = only("<a/>");
		assertTrue(span.selfClosing());
		assertEquals(0, span.tagStart());
		assertEquals(4, span.contentStart(), "a self-closing element's content starts and ends past its tag");
		assertEquals(4, span.contentEnd());
	}

	@Test
	void marksAnElementSpacedBeforeItsSlashAsSelfClosingToo() {
		assertTrue(only("<a />").selfClosing());
	}

	@Test
	void treatsAnEmptyPairOfTagsAsNotSelfClosing() {
		Span span = only("<a></a>");
		assertFalse(span.selfClosing());
		assertEquals(span.contentStart(), span.contentEnd(), "the element has no content between its tags");
	}

	@Test
	void ordersSpansAsAPreOrderWalkVisitsThem() {
		List<Span> spans = XmlElementSpans.of("<a><b><c/></b><d/></a>");
		assertEquals(4, spans.size());
		assertEquals(0, spans.get(0).tagStart(), "the root comes first");
		assertEquals(3, spans.get(1).tagStart(), "then its first child");
		assertEquals(6, spans.get(2).tagStart(), "then that child's own child, before the root's second child");
		assertEquals(14, spans.get(3).tagStart());
	}

	/**
	 * A {@code >} inside an attribute value does not close the tag. Reading it as if
	 * it did would shift every following offset and splice the adoption's block into
	 * the middle of an unrelated element.
	 */
	@Test
	void doesNotEndATagAtAGreaterThanInsideAnAttributeValue() {
		String xml = "<a config=\"x > y\">text</a>";
		Span span = only(xml);
		assertEquals(xml.indexOf("text"), span.contentStart());
		assertEquals(xml.indexOf("</a>"), span.contentEnd());
	}

	@Test
	void handlesSingleQuotedAttributeValues() {
		String xml = "<a config='x > y'>text</a>";
		assertEquals(xml.indexOf("text"), only(xml).contentStart());
	}

	@Test
	void skipsMarkupInsideAComment() {
		String xml = "<a><!-- <b/> --><c/></a>";
		List<Span> spans = XmlElementSpans.of(xml);
		assertEquals(2, spans.size(), "the commented-out element is not an element: " + spans);
		assertEquals(xml.indexOf("<c/>"), spans.get(1).tagStart());
	}

	@Test
	void skipsMarkupInsideACdataSection() {
		String xml = "<a><![CDATA[</a><b/>]]></a>";
		List<Span> spans = XmlElementSpans.of(xml);
		assertEquals(1, spans.size(), "CDATA content is text, not markup: " + spans);
		assertEquals(xml.lastIndexOf("</a>"), spans.get(0).contentEnd());
	}

	@Test
	void skipsTheXmlDeclaration() {
		String xml = "<?xml version=\"1.0\"?><a/>";
		Span span = only(xml);
		assertEquals(xml.indexOf("<a/>"), span.tagStart());
	}

	@Test
	void anEndTagClosingNothingIsRejected() {
		assertThrows(AdoptionException.class, () -> XmlElementSpans.of("<a></a></b>"));
	}

	@Test
	void anElementLeftOpenIsRejected() {
		assertThrows(AdoptionException.class, () -> XmlElementSpans.of("<a><b></a>"));
	}

	private Span only(String xml) {
		List<Span> spans = XmlElementSpans.of(xml);
		assertEquals(1, spans.size(), "expected exactly one element: " + spans);
		return spans.get(0);
	}
}
