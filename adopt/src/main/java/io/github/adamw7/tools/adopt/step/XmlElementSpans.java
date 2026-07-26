package io.github.adamw7.tools.adopt.step;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

import io.github.adamw7.tools.adopt.AdoptionException;

/**
 * The character range every element of an XML document occupies in its source
 * text, in document order. {@link PomDocument} needs these ranges to splice an
 * edit into the bytes a {@code pom.xml} already holds: a DOM carries no record of
 * where its elements were read from, and re-serialising the whole document to get
 * the edit out reformats every line it touches — attributes that were spread over
 * several lines collapse onto one, {@code <rule />} becomes {@code <rule/>} — so
 * an adoption commit that adds a dozen lines shows up as a diff across the file.
 *
 * <p>The scan is deliberately lexical rather than a second parse: it only needs to
 * know where each element's start tag, content, and end tag begin, which the JAXP
 * parser does not report and a {@code Location}-based scan only reports to the
 * precision of the implementation. Comments, processing instructions, CDATA
 * sections, and the {@code >} characters that appear inside quoted attribute
 * values are recognised so they cannot be mistaken for markup. The document has
 * already been parsed by {@link PomDocument} before this runs, so it is known to
 * be well-formed and anything unbalanced here is a defect rather than bad input.
 */
final class XmlElementSpans {

	/**
	 * Where a single element sits in the source text.
	 *
	 * @param tagStart     index of the {@code <} that opens the start tag
	 * @param contentStart index just past the start tag's {@code >}
	 * @param contentEnd   index of the {@code <} that opens the end tag, equal to
	 *                     {@code contentStart} for an element written as
	 *                     {@code <name/>}
	 * @param selfClosing  whether the element was written as {@code <name/>} and so
	 *                     has no end tag for an edit to be inserted before
	 */
	record Span(int tagStart, int contentStart, int contentEnd, boolean selfClosing) {
	}

	private static final String COMMENT_START = "<!--";
	private static final String COMMENT_END = "-->";
	private static final String CDATA_START = "<![CDATA[";
	private static final String CDATA_END = "]]>";
	private static final String INSTRUCTION_START = "<?";
	private static final String INSTRUCTION_END = "?>";
	private static final String END_TAG_START = "</";
	private static final String SELF_CLOSING_END = "/>";
	private static final int NOTHING_LEFT = -1;

	/** A start tag whose end tag has not been reached yet. */
	private record Pending(int tagStart, int contentStart, int slot) {
	}

	private final String xml;
	private final List<Span> spans = new ArrayList<>();
	private final Deque<Pending> unclosed = new ArrayDeque<>();

	private XmlElementSpans(String xml) {
		this.xml = xml;
	}

	/** @return one span per element, in document order — the order a pre-order DOM walk visits them in */
	static List<Span> of(String xml) {
		XmlElementSpans scan = new XmlElementSpans(xml);
		scan.run();
		return scan.completed();
	}

	private void run() {
		int index = 0;
		while (index >= 0 && index < xml.length()) {
			index = next(index);
		}
	}

	private int next(int from) {
		int bracket = xml.indexOf('<', from);
		return bracket < 0 ? NOTHING_LEFT : markup(bracket);
	}

	/**
	 * Dispatches on what the {@code <} opens. The comment, CDATA, and processing
	 * instruction cases are skipped whole rather than scanned, so markup-looking
	 * text inside them is never mistaken for an element.
	 */
	private int markup(int start) {
		if (xml.startsWith(COMMENT_START, start)) {
			return after(start, COMMENT_END);
		}
		if (xml.startsWith(CDATA_START, start)) {
			return after(start, CDATA_END);
		}
		if (xml.startsWith(INSTRUCTION_START, start)) {
			return after(start, INSTRUCTION_END);
		}
		if (xml.startsWith(END_TAG_START, start)) {
			return endTag(start);
		}
		return startTag(start);
	}

	private int after(int start, String terminator) {
		int end = xml.indexOf(terminator, start);
		return end < 0 ? NOTHING_LEFT : end + terminator.length();
	}

	/**
	 * Records the element in document order right away — before its end tag is
	 * known — by reserving its slot, so a parent still precedes the children whose
	 * tags close first.
	 */
	private int startTag(int start) {
		int close = tagEnd(start);
		if (close < 0) {
			return NOTHING_LEFT;
		}
		int contentStart = close + 1;
		if (xml.startsWith(SELF_CLOSING_END, close - 1)) {
			spans.add(new Span(start, contentStart, contentStart, true));
		} else {
			spans.add(null);
			unclosed.addLast(new Pending(start, contentStart, spans.size() - 1));
		}
		return contentStart;
	}

	private int endTag(int start) {
		int close = xml.indexOf('>', start);
		if (close < 0) {
			return NOTHING_LEFT;
		}
		fill(start);
		return close + 1;
	}

	private void fill(int endTagStart) {
		Pending pending = unclosed.pollLast();
		if (pending == null) {
			throw new AdoptionException("Unbalanced XML: an end tag at offset " + endTagStart + " closes nothing");
		}
		spans.set(pending.slot(), new Span(pending.tagStart(), pending.contentStart(), endTagStart, false));
	}

	/**
	 * The {@code >} that closes a tag, skipping the ones inside quoted attribute
	 * values — a {@code <plugin config="a>b">} would otherwise be cut short and
	 * every following offset shifted.
	 */
	private int tagEnd(int tagStart) {
		char quote = 0;
		int index = tagStart + 1;
		while (index < xml.length()) {
			char character = xml.charAt(index);
			if (quote != 0) {
				quote = character == quote ? 0 : quote;
			} else if (character == '"' || character == '\'') {
				quote = character;
			} else if (character == '>') {
				return index;
			}
			index++;
		}
		return NOTHING_LEFT;
	}

	private List<Span> completed() {
		if (!unclosed.isEmpty()) {
			throw new AdoptionException("Unbalanced XML: " + unclosed.size() + " element(s) were never closed");
		}
		return List.copyOf(spans);
	}
}
