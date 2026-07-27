package io.github.adamw7.tools.adopt.step;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

import io.github.adamw7.tools.adopt.AdoptionException;
import io.github.adamw7.tools.adopt.AdoptionFiles;
import io.github.adamw7.tools.adopt.step.XmlElementSpans.Span;

/**
 * A {@code pom.xml} read for editing and written back without reformatting.
 * Callers find the element they want to extend with the query methods and add
 * markup under it with {@link #insertUnder}; every whitespace character, the XML
 * declaration, the trailing newline, and the line terminator survive
 * {@link #write()} untouched, so the adoption commit shows only the block that was
 * added.
 *
 * <p>The DOM is only ever read — it answers which elements the POM declares and
 * how they nest — while the edit itself is text spliced into the bytes the file
 * already held, at the source offsets {@link XmlElementSpans} reports. Writing an
 * edited DOM out whole cannot keep that promise however carefully the serialiser
 * is configured, because the details it reformats are not in the DOM to begin
 * with: a start tag spread over several lines and an empty element written
 * {@code <rule />} both come back normalised. Keeping the edit textual also means
 * added elements inherit the POM's default namespace rather than having to be
 * qualified.
 *
 * <p>Added markup arrives one element per line, indented by
 * {@link #FRAGMENT_INDENT} per nesting level, and is re-indented to the document's
 * own unit (detected from the file, defaulting to two spaces) at the depth it
 * lands at. It is inserted after the parent's last child, so the parent's own
 * closing indentation stays put.
 */
final class PomDocument {

	/** The indentation one nesting level of a caller's fragment is written with. */
	static final String FRAGMENT_INDENT = "  ";

	private static final String DEFAULT_INDENT_UNIT = "  ";
	private static final String DESCRIPTION = "POM";

	/** A replacement of {@code [start, end)} in the original text; a pure insertion when they are equal. */
	private record Edit(int start, int end, String replacement) {
	}

	private final Path file;
	private final String original;
	private final Document document;
	private final String indentUnit;

	/**
	 * Where each element the file carries sits in it, so an edit can be spliced beside
	 * it. Paired by document order: a pre-order DOM walk and the lexical scan visit the
	 * same elements in the same sequence.
	 */
	private final Map<Element, Span> spans;

	/** The markup to add inside each element, in fragment indentation, awaiting {@link #write()}. */
	private final Map<Element, String> additions = new IdentityHashMap<>();

	private PomDocument(Path file, String original, Document document) {
		this.file = file;
		this.original = original;
		this.document = document;
		this.indentUnit = detectIndentUnit(document.getDocumentElement());
		this.spans = spansOf(document, original);
	}

	static PomDocument read(Path file) {
		return new PomDocument(file, AdoptionFiles.read(file, DESCRIPTION), parse(file));
	}

	Element root() {
		return document.getDocumentElement();
	}

	/**
	 * Every {@code plugin} the POM declares, wherever it sits: the build, plugin
	 * management, or a profile. A project can wire a plugin somewhere other than its
	 * build — behind an opt-in profile, most often — and a check that only looked there
	 * would conclude the plugin is absent and add a second declaration of it.
	 */
	List<Element> plugins() {
		return preOrder(root()).stream()
				.filter(element -> "plugin".equals(element.getLocalName()))
				.toList();
	}

	/**
	 * The element the nested {@code path} names below the root, or empty when the POM
	 * does not carry every level of it. The counterpart of {@link #insertUnder}: a
	 * caller asks about the very place it would add to, so what it inspects and what
	 * it edits cannot drift apart.
	 */
	Optional<Element> at(List<String> path) {
		Optional<Element> element = Optional.of(root());
		for (String name : path) {
			element = element.flatMap(parent -> child(parent, name));
		}
		return element;
	}

	static Optional<Element> child(Element parent, String name) {
		return children(parent, name).stream().findFirst();
	}

	static List<Element> children(Element parent, String name) {
		return childNodes(parent)
				.filter(node -> node instanceof Element element && name.equals(element.getLocalName()))
				.map(Element.class::cast)
				.toList();
	}

	/** @return whether the element declares exactly this {@code artifactId} */
	static boolean hasArtifactId(Element element, String artifactId) {
		return child(element, "artifactId")
				.map(Element::getTextContent)
				.map(String::strip)
				.filter(artifactId::equals)
				.isPresent();
	}

	/** @return {@code body} wrapped in a {@code name} element, its lines indented one level deeper */
	static String wrapped(String name, String body) {
		return "<" + name + ">\n" + indented(body) + "\n</" + name + ">";
	}

	/**
	 * Adds {@code fragment} inside {@code parent}'s nested {@code path}, creating
	 * whichever of those levels the POM does not carry yet — so a POM with no
	 * {@code build} at all and one that already has {@code build/plugins} are the same
	 * call. Nothing is written until {@link #write()}.
	 */
	void insertUnder(Element parent, List<String> path, String fragment) {
		if (path.isEmpty()) {
			additions.merge(parent, fragment, (existing, added) -> existing + "\n" + added);
			return;
		}
		List<String> rest = path.subList(1, path.size());
		child(parent, path.get(0)).ifPresentOrElse(
				element -> insertUnder(element, rest, fragment),
				() -> insertUnder(parent, List.of(), wrap(path, fragment)));
	}

	/**
	 * Writes the original text back with the added markup spliced into it, so every
	 * byte the file already held — its XML declaration, its attribute layout, its
	 * empty-element style, its trailing newline — is preserved by construction rather
	 * than by a serialiser that has to be talked out of reformatting. A POM nothing was
	 * added to is written back unchanged.
	 *
	 * <p>The edits are applied last-first so that each one's offsets, taken from the
	 * unmodified text, are still correct when it is applied.
	 */
	void write() {
		StringBuilder content = new StringBuilder(original);
		additions.entrySet().stream()
				.map(addition -> edit(addition.getKey(), addition.getValue()))
				.sorted(Comparator.comparingInt(Edit::start).reversed())
				.forEach(edit -> content.replace(edit.start(), edit.end(), edit.replacement()));
		AdoptionFiles.write(file, content.toString(), DESCRIPTION);
	}

	/** @return {@code fragment} wrapped in the named elements, outermost first */
	private static String wrap(List<String> names, String fragment) {
		String wrapped = fragment;
		for (String name : names.reversed()) {
			wrapped = wrapped(name, wrapped);
		}
		return wrapped;
	}

	private static String indented(String fragment) {
		return fragment.lines().map(line -> FRAGMENT_INDENT + line).collect(Collectors.joining("\n"));
	}

	/**
	 * Where the markup goes. An element that already had children takes it after the
	 * last of them, leaving the whitespace before the end tag — and so that tag's
	 * indentation — exactly as it was. An element that was empty or held only
	 * whitespace has that content replaced instead, because there is no last child to
	 * follow and its end tag needs indenting onto a line of its own; an element written
	 * {@code <plugins/>} additionally has to grow an end tag to hold the markup at all.
	 */
	private Edit edit(Element parent, String addition) {
		Span span = spans.get(parent);
		int depth = depthOf(parent);
		String fragment = fragment(addition, depth + 1);
		String closingIndent = newlineIndent(depth);
		if (span.selfClosing()) {
			return edit(span.tagStart(), span.contentStart(),
					reopened(span) + fragment + closingIndent + endTag(parent));
		}
		int lastChildEnd = beforeTrailingWhitespace(span);
		if (lastChildEnd == span.contentStart()) {
			return edit(span.contentStart(), span.contentEnd(), fragment + closingIndent);
		}
		return edit(lastChildEnd, lastChildEnd, fragment);
	}

	/**
	 * The addition's lines, each on a line of its own indented to {@code depth} plus
	 * its own nesting within the fragment, in the document's indentation unit rather
	 * than the fragment's.
	 */
	private String fragment(String addition, int depth) {
		return addition.lines()
				.map(line -> newlineIndent(depth + levelOf(line)) + line.stripLeading())
				.collect(Collectors.joining());
	}

	private int levelOf(String line) {
		return (line.length() - line.stripLeading().length()) / FRAGMENT_INDENT.length();
	}

	/**
	 * A fragment is always LF — XML parsing normalises {@code \r\n} to {@code \n} and
	 * the templates are written with LF — so {@link LineTerminators} puts the file's own
	 * terminator back rather than leaving LF lines in an otherwise CRLF POM.
	 */
	private Edit edit(int start, int end, String replacement) {
		return new Edit(start, end, LineTerminators.matching(replacement, original));
	}

	/** The start tag of a {@code <name/>} element, reopened as {@code <name>} to take children. */
	private String reopened(Span span) {
		String startTag = original.substring(span.tagStart(), span.contentStart());
		return startTag.substring(0, startTag.length() - 2).stripTrailing() + ">";
	}

	private String endTag(Element element) {
		return "</" + element.getTagName() + ">";
	}

	private int beforeTrailingWhitespace(Span span) {
		int end = span.contentEnd();
		while (end > span.contentStart() && Character.isWhitespace(original.charAt(end - 1))) {
			end--;
		}
		return end;
	}

	private static Map<Element, Span> spansOf(Document document, String original) {
		List<Element> elements = preOrder(document.getDocumentElement());
		List<Span> spans = XmlElementSpans.of(original);
		if (elements.size() != spans.size()) {
			throw new AdoptionException("Could not map the POM's " + elements.size() + " parsed elements onto the "
					+ spans.size() + " found in its text");
		}
		Map<Element, Span> byElement = new IdentityHashMap<>();
		IntStream.range(0, elements.size()).forEach(index -> byElement.put(elements.get(index), spans.get(index)));
		return byElement;
	}

	private static List<Element> preOrder(Element root) {
		List<Element> elements = new ArrayList<>();
		collect(root, elements);
		return List.copyOf(elements);
	}

	private static void collect(Element element, List<Element> into) {
		into.add(element);
		elementChildren(element).forEach(child -> collect(child, into));
	}

	private static List<Element> elementChildren(Element parent) {
		return childNodes(parent).filter(Element.class::isInstance).map(Element.class::cast).toList();
	}

	private static Stream<Node> childNodes(Element parent) {
		NodeList nodes = parent.getChildNodes();
		return IntStream.range(0, nodes.getLength()).mapToObj(nodes::item);
	}

	private String newlineIndent(int depth) {
		return "\n" + indentUnit.repeat(depth);
	}

	private static int depthOf(Node node) {
		int depth = 0;
		Node parent = node.getParentNode();
		while (parent != null && parent.getNodeType() == Node.ELEMENT_NODE) {
			depth++;
			parent = parent.getParentNode();
		}
		return depth;
	}

	/**
	 * The indentation of a single nesting level, read from the first top-level
	 * element's leading whitespace, or two spaces when the POM carries none.
	 */
	private static String detectIndentUnit(Element root) {
		return childNodes(root)
				.map(PomDocument::leadingIndentOf)
				.filter(unit -> !unit.isEmpty())
				.findFirst()
				.orElse(DEFAULT_INDENT_UNIT);
	}

	private static String leadingIndentOf(Node node) {
		if (node.getNodeType() != Node.ELEMENT_NODE) {
			return "";
		}
		Node previous = node.getPreviousSibling();
		if (!isWhitespaceText(previous)) {
			return "";
		}
		String text = previous.getTextContent();
		int newline = text.lastIndexOf('\n');
		return newline < 0 ? "" : text.substring(newline + 1);
	}

	private static boolean isWhitespaceText(Node node) {
		return node != null && node.getNodeType() == Node.TEXT_NODE && node.getTextContent().isBlank();
	}

	private static Document parse(Path file) {
		try {
			return builder().parse(file.toFile());
		} catch (IOException | SAXException e) {
			throw new AdoptionException("Could not read POM: " + file, e);
		}
	}

	private static DocumentBuilder builder() {
		try {
			DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
			factory.setNamespaceAware(true);
			factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
			factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
			factory.setExpandEntityReferences(false);
			return factory.newDocumentBuilder();
		} catch (ParserConfigurationException e) {
			throw new AdoptionException("Could not configure XML parser", e);
		}
	}

}
