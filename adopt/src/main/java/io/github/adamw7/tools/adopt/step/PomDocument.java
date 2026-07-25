package io.github.adamw7.tools.adopt.step;

import java.io.IOException;
import java.io.StringWriter;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
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

/**
 * A {@code pom.xml} parsed for editing and written back without reformatting.
 * Callers append elements through {@link #appendElement} and {@link #appendText};
 * everything the file already held — every whitespace node, its XML declaration,
 * its trailing newline, and its line terminator — survives {@link #write()}
 * untouched, so the adoption commit shows only the block that was added rather
 * than a reformat of the whole file.
 *
 * <p>Each appended element is preceded by a newline and an indentation matching
 * the document's own unit (detected from the file, defaulting to two spaces), and
 * is inserted before the parent's closing indentation so that closing tag stays
 * put. Every container is attached to its parent before its children are added,
 * so an element's depth — and therefore its indentation — is known as soon as it
 * is appended. The edit runs on the JDK's namespace-aware DOM, so no third-party
 * XML library is needed and new elements join the POM's default namespace.
 */
final class PomDocument {

	private static final String DEFAULT_INDENT_UNIT = "  ";
	private static final String DESCRIPTION = "POM";

	private final Path file;
	private final String original;
	private final Document document;
	private final String namespace;
	private final String indentUnit;

	private PomDocument(Path file, String original, Document document) {
		this.file = file;
		this.original = original;
		this.document = document;
		this.namespace = document.getDocumentElement().getNamespaceURI();
		this.indentUnit = detectIndentUnit(document.getDocumentElement());
	}

	static PomDocument read(Path file) {
		return new PomDocument(file, AdoptionFiles.read(file, DESCRIPTION), parse(file));
	}

	/** The {@code build/plugins} element, creating either level when the POM lacks it. */
	Element pluginsElement() {
		Element build = childOrCreate(document.getDocumentElement(), "build");
		return childOrCreate(build, "plugins");
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
	 * Writes the document back verbatim: the transformer's own indentation is left
	 * off and the parsed whitespace nodes are kept, so only the added elements
	 * differ from the original, whose XML declaration and trailing newline are
	 * carried over exactly. XML parsing normalizes {@code \r\n} to {@code \n}, so
	 * {@link LineTerminators} puts the original terminator back rather than flipping
	 * a CRLF POM to LF and reformatting the whole file.
	 */
	void write() {
		String content = matchTrailingNewline(declarationPrefix() + serialize());
		AdoptionFiles.write(file, LineTerminators.matching(content, original), DESCRIPTION);
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

	private String serialize() {
		try {
			StringWriter writer = new StringWriter();
			transformer().transform(new DOMSource(document), new StreamResult(writer));
			return writer.toString();
		} catch (TransformerException e) {
			throw new AdoptionException("Could not write POM: " + file, e);
		}
	}

	private static Transformer transformer() throws TransformerException {
		TransformerFactory factory = TransformerFactory.newInstance();
		factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
		Transformer transformer = factory.newTransformer();
		transformer.setOutputProperty(OutputKeys.INDENT, "no");
		transformer.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "yes");
		return transformer;
	}

	/**
	 * The XML declaration the original opened with, up to and including its line
	 * terminator, or empty when it had none — so the transformer cannot invent one
	 * (a spurious first-line change) on a POM that started with {@code <project>}.
	 */
	private String declarationPrefix() {
		if (!original.stripLeading().startsWith("<?xml")) {
			return "";
		}
		int end = original.indexOf("?>");
		if (end < 0) {
			return "";
		}
		int afterTerminator = lineTerminatorEnd(original, end + 2);
		return original.substring(0, afterTerminator) + (afterTerminator == end + 2 ? "\n" : "");
	}

	private static int lineTerminatorEnd(String text, int from) {
		int index = from;
		if (index < text.length() && text.charAt(index) == '\r') {
			index++;
		}
		if (index < text.length() && text.charAt(index) == '\n') {
			index++;
		}
		return index;
	}

	private String matchTrailingNewline(String content) {
		boolean originalEnds = original.endsWith("\n") || original.endsWith("\r");
		boolean contentEnds = content.endsWith("\n") || content.endsWith("\r");
		return originalEnds && !contentEnds ? content + "\n" : content;
	}
}
