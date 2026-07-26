package io.github.adamw7.tools.adopt.step;

import java.io.IOException;
import java.io.StringWriter;
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
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

import io.github.adamw7.tools.adopt.AdoptionException;
import io.github.adamw7.tools.adopt.AdoptionFiles;
import io.github.adamw7.tools.adopt.step.XmlElementSpans.Span;

/**
 * A {@code pom.xml} parsed for editing and written back without reformatting.
 * Callers append elements through {@link #appendElement} and {@link #appendText};
 * everything the file already held — every whitespace node, its XML declaration,
 * its trailing newline, and its line terminator — survives {@link #write()}
 * untouched, so the adoption commit shows only the block that was added rather
 * than a reformat of the whole file.
 *
 * <p>Only the added elements are serialised, and their text is spliced into the
 * bytes the file already held. Writing the edited DOM out whole cannot keep that
 * promise however carefully the serialiser is configured, because the details it
 * would reformat are not in the DOM to begin with: a start tag spread over several
 * lines and an empty element written {@code <rule />} both come back normalised,
 * turning a fourteen-line addition into a diff that touches the whole file. The
 * source offsets the splice needs come from {@link XmlElementSpans}.
 *
 * <p>Each appended element is preceded by a newline and an indentation matching
 * the document's own unit (detected from the file, defaulting to two spaces), and
 * is inserted after the parent's last child so the parent's own closing
 * indentation stays put. Every container is attached to its parent before its
 * children are added, so an element's depth — and therefore its indentation — is
 * known as soon as it is appended. The edit runs on the JDK's namespace-aware DOM,
 * so no third-party XML library is needed and new elements join the POM's default
 * namespace.
 */
final class PomDocument {

	private static final String DEFAULT_INDENT_UNIT = "  ";
	private static final String DESCRIPTION = "POM";

	/** A replacement of {@code [start, end)} in the original text; a pure insertion when they are equal. */
	private record Edit(int start, int end, String replacement) {
	}

	private final Path file;
	private final String original;
	private final Document document;
	private final String namespace;
	private final String indentUnit;

	/**
	 * Where each element the file already carried sits in it. An element absent from
	 * this map is one the caller appended, which is what makes the added subtrees
	 * identifiable at {@link #write()} time without the callers having to declare
	 * them.
	 */
	private final Map<Element, Span> spans;

	private PomDocument(Path file, String original, Document document) {
		this.file = file;
		this.original = original;
		this.document = document;
		this.namespace = document.getDocumentElement().getNamespaceURI();
		this.indentUnit = detectIndentUnit(document.getDocumentElement());
		this.spans = spansOf(document, original);
	}

	static PomDocument read(Path file) {
		return new PomDocument(file, AdoptionFiles.read(file, DESCRIPTION), parse(file));
	}

	/** The {@code build/plugins} element, creating either level when the POM lacks it. */
	Element pluginsElement() {
		Element build = childOrCreate(document.getDocumentElement(), "build");
		return childOrCreate(build, "plugins");
	}

	/**
	 * Every {@code plugin} the POM declares, wherever it sits: the build, plugin
	 * management, or a profile. A project can wire a plugin somewhere other than
	 * {@link #pluginsElement()} — behind an opt-in profile, most often — and a check
	 * that only looked there would conclude the plugin is absent and add a second
	 * declaration of it.
	 */
	List<Element> plugins() {
		return preOrder(document.getDocumentElement()).stream()
				.filter(element -> "plugin".equals(element.getLocalName()))
				.toList();
	}

	Element childOrCreate(Element parent, String name) {
		return child(parent, name).orElseGet(() -> appendElement(parent, name));
	}

	Element appendElement(Element parent, String name) {
		return appendChild(parent, create(name));
	}

	void appendText(Element parent, String name, String text) {
		Element element = create(name);
		element.setTextContent(text);
		appendChild(parent, element);
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

	/** @return the element's {@code artifactId} text, when it declares one */
	static boolean hasArtifactId(Element element, String artifactId) {
		return child(element, "artifactId")
				.map(Element::getTextContent)
				.map(String::strip)
				.filter(artifactId::equals)
				.isPresent();
	}

	/**
	 * Writes the original text back with the added subtrees spliced into it, so
	 * every byte the file already held — its XML declaration, its attribute layout,
	 * its empty-element style, its trailing newline — is preserved by construction
	 * rather than by a serialiser that has to be talked out of reformatting. A POM
	 * nothing was appended to is written back unchanged.
	 *
	 * <p>The edits are applied last-first so that each one's offsets, taken from the
	 * unmodified text, are still correct when it is applied.
	 */
	void write() {
		StringBuilder content = new StringBuilder(original);
		edits().stream()
				.sorted(Comparator.comparingInt(Edit::start).reversed())
				.forEach(edit -> content.replace(edit.start(), edit.end(), edit.replacement()));
		AdoptionFiles.write(file, content.toString(), DESCRIPTION);
	}

	/** One edit per element of the original document that was appended to. */
	private List<Edit> edits() {
		return preOrder(document.getDocumentElement()).stream()
				.filter(spans::containsKey)
				.map(this::editFor)
				.flatMap(Optional::stream)
				.toList();
	}

	private Optional<Edit> editFor(Element parent) {
		List<Element> added = elementChildren(parent).stream().filter(child -> !spans.containsKey(child)).toList();
		if (added.isEmpty()) {
			return Optional.empty();
		}
		return Optional.of(edit(parent, added.stream().map(this::fragmentOf).collect(Collectors.joining())));
	}

	/** The added subtree's text, indented to its own depth the way the DOM path indented it. */
	private String fragmentOf(Element added) {
		return newlineIndent(depthOf(added)) + serialize(added);
	}

	/**
	 * Where the fragment goes. An element that already had children takes it after
	 * the last of them, leaving the whitespace before the end tag — and so that
	 * tag's indentation — exactly as it was. An element that was empty or held only
	 * whitespace has that content replaced instead, because there is no last child
	 * to follow and its end tag needs indenting onto a line of its own; an element
	 * written {@code <plugins/>} additionally has to grow an end tag to hold the
	 * fragment at all.
	 */
	private Edit edit(Element parent, String fragment) {
		Span span = spans.get(parent);
		String closingIndent = newlineIndent(depthOf(parent));
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
	 * XML parsing normalises {@code \r\n} to {@code \n}, so a serialised fragment is
	 * always LF; {@link LineTerminators} puts the file's own terminator back rather
	 * than leaving LF lines in an otherwise CRLF POM.
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

	/**
	 * Pairs each element the file carried with its place in the text by document
	 * order: a pre-order DOM walk and the lexical scan visit the same elements in
	 * the same sequence, so the two lists line up index for index without having to
	 * match names or attributes.
	 */
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

	private Element appendChild(Element parent, Element child) {
		Node closingIndent = trailingWhitespace(parent);
		Node childIndent = document.createTextNode(newlineIndent(depthOf(parent) + 1));
		if (closingIndent == null) {
			parent.appendChild(childIndent);
			parent.appendChild(child);
			parent.appendChild(document.createTextNode(newlineIndent(depthOf(parent))));
		} else {
			parent.insertBefore(childIndent, closingIndent);
			parent.insertBefore(child, closingIndent);
		}
		return child;
	}

	private String newlineIndent(int depth) {
		return "\n" + indentUnit.repeat(depth);
	}

	private Element create(String name) {
		return namespace == null ? document.createElement(name) : document.createElementNS(namespace, name);
	}

	private static Node trailingWhitespace(Element parent) {
		Node last = parent.getLastChild();
		return isWhitespaceText(last) ? last : null;
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

	private String serialize(Element element) {
		try {
			StringWriter writer = new StringWriter();
			transformer().transform(new DOMSource(element), new StreamResult(writer));
			return withoutRepeatedNamespace(writer.toString());
		} catch (TransformerException e) {
			throw new AdoptionException("Could not write POM: " + file, e);
		}
	}

	/**
	 * Serialising a subtree on its own leaves the serialiser no way to know the
	 * POM's root already declares the default namespace, so it restates it and the
	 * added block would read {@code <build xmlns="http://maven.apache.org/POM/4.0.0">}.
	 * The fragment is spliced back under that root, which still carries the
	 * declaration, so the repeat is dropped. Only the fragment's own root can carry
	 * it — its descendants inherit it — so only the first occurrence is removed.
	 */
	private String withoutRepeatedNamespace(String fragment) {
		if (namespace == null) {
			return fragment;
		}
		String declaration = " xmlns=\"" + namespace + "\"";
		int at = fragment.indexOf(declaration);
		return at < 0 ? fragment : fragment.substring(0, at) + fragment.substring(at + declaration.length());
	}

	private static Transformer transformer() throws TransformerException {
		TransformerFactory factory = TransformerFactory.newInstance();
		factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
		Transformer transformer = factory.newTransformer();
		transformer.setOutputProperty(OutputKeys.INDENT, "no");
		transformer.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "yes");
		return transformer;
	}

}
