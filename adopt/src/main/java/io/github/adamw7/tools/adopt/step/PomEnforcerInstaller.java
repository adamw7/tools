package io.github.adamw7.tools.adopt.step;

import java.io.IOException;
import java.io.InputStream;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Properties;

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

/**
 * Adds the {@code claude-code-enforcer} to a Maven project's {@code pom.xml} by
 * wiring the {@code maven-enforcer-plugin} with the {@code claudeMdFormat} rule,
 * so the adopted repository fails its build if the freshly generated
 * {@code CLAUDE.md} is missing or malformed.
 *
 * <p>The edit is performed on the JDK's DOM so no third-party XML library is
 * needed, is namespace-aware so the new elements join the POM's default
 * namespace, and is idempotent: a POM that already wires the rule is left
 * untouched. A POM that already uses the {@code maven-enforcer-plugin} for other
 * rules is augmented in place rather than skipped, so the rule is still wired in.
 *
 * <p>The existing document is preserved verbatim — every original whitespace and
 * line is left untouched — and only the newly added elements are indented, to the
 * style the POM already uses. The adoption commit therefore shows just the
 * enforcer block being added rather than a reformat of the whole file.
 */
public class PomEnforcerInstaller {

	static final String ENFORCER_GROUP_ID = "org.apache.maven.plugins";
	static final String ENFORCER_ARTIFACT_ID = "maven-enforcer-plugin";
	static final String ENFORCER_VERSION = "3.6.3";
	static final String RULE_ARTIFACT_ID = "tools.claude-code-enforcer";
	static final String RULE_GROUP_ID = "io.github.adamw7";
	static final String CLAUDE_MD_FILE = "${project.basedir}/CLAUDE.md";
	static final String BUILD_PROPERTIES = "/adopt-build.properties";
	static final String RULE_VERSION_KEY = "enforcer.rule.version";

	private final String ruleVersion;

	public PomEnforcerInstaller() {
		this(buildRuleVersion());
	}

	public PomEnforcerInstaller(String ruleVersion) {
		this.ruleVersion = ruleVersion;
	}

	/**
	 * Reads the enforcer rule version wired into adopted POMs from the build
	 * metadata that Maven filters into {@value #BUILD_PROPERTIES} at build time, so
	 * the dependency is pinned to the exact {@code tools} release running the
	 * adoption — and is therefore resolvable from the same repository that
	 * published it — rather than to a hardcoded literal that silently drifts as the
	 * project is versioned.
	 */
	private static String buildRuleVersion() {
		try (InputStream stream = PomEnforcerInstaller.class.getResourceAsStream(BUILD_PROPERTIES)) {
			return readRuleVersion(stream);
		} catch (IOException e) {
			throw new AdoptionException("Could not read build metadata: " + BUILD_PROPERTIES, e);
		}
	}

	private static String readRuleVersion(InputStream stream) throws IOException {
		if (stream == null) {
			throw new AdoptionException("Build metadata not on the classpath: " + BUILD_PROPERTIES
					+ " (build the module so its resources are filtered)");
		}
		Properties properties = new Properties();
		properties.load(stream);
		String version = properties.getProperty(RULE_VERSION_KEY, "").strip();
		if (version.isEmpty() || version.startsWith("${")) {
			throw new AdoptionException(
					RULE_VERSION_KEY + " was not filtered into " + BUILD_PROPERTIES + " (found: '" + version + "')");
		}
		return version;
	}

	/**
	 * @return {@code true} when the rule was wired in, {@code false} when the POM
	 *         already declared the {@code claude-code-enforcer} rule and was left
	 *         unchanged.
	 */
	public boolean install(Path pomFile) {
		String original = readText(pomFile);
		Document document = parse(pomFile);
		PomEditor editor = new PomEditor(document);
		Element plugins = editor.pluginsElement();
		if (declaresClaudeRule(plugins)) {
			return false;
		}
		wireEnforcer(editor, plugins);
		write(document, pomFile, original);
		return true;
	}

	private boolean declaresClaudeRule(Element plugins) {
		return children(plugins, "plugin").stream().anyMatch(this::declaresRuleDependency);
	}

	private boolean declaresRuleDependency(Element plugin) {
		return child(plugin, "dependencies").stream()
				.flatMap(dependencies -> children(dependencies, "dependency").stream())
				.anyMatch(this::isRuleDependency);
	}

	private boolean isRuleDependency(Element dependency) {
		return child(dependency, "artifactId")
				.map(Element::getTextContent)
				.map(String::strip)
				.filter(RULE_ARTIFACT_ID::equals)
				.isPresent();
	}

	/**
	 * Adds the rule dependency and its {@code enforce} execution to the POM's
	 * {@code maven-enforcer-plugin}, reusing an existing plugin declaration when
	 * one is present so the project keeps a single enforcer plugin entry.
	 */
	private void wireEnforcer(PomEditor editor, Element plugins) {
		Element plugin = findEnforcerPlugin(plugins).orElseGet(() -> createEnforcerPlugin(editor, plugins));
		ruleDependency(editor, editor.childOrCreate(plugin, "dependencies"));
		enforceExecution(editor, editor.childOrCreate(plugin, "executions"));
	}

	private Optional<Element> findEnforcerPlugin(Element plugins) {
		return children(plugins, "plugin").stream()
				.filter(this::isEnforcerPlugin)
				.findFirst();
	}

	private boolean isEnforcerPlugin(Element plugin) {
		return child(plugin, "artifactId")
				.map(Element::getTextContent)
				.map(String::strip)
				.filter(ENFORCER_ARTIFACT_ID::equals)
				.isPresent();
	}

	private Element createEnforcerPlugin(PomEditor editor, Element plugins) {
		Element plugin = editor.appendElement(plugins, "plugin");
		editor.appendText(plugin, "groupId", ENFORCER_GROUP_ID);
		editor.appendText(plugin, "artifactId", ENFORCER_ARTIFACT_ID);
		editor.appendText(plugin, "version", ENFORCER_VERSION);
		return plugin;
	}

	private void ruleDependency(PomEditor editor, Element dependencies) {
		Element dependency = editor.appendElement(dependencies, "dependency");
		editor.appendText(dependency, "groupId", RULE_GROUP_ID);
		editor.appendText(dependency, "artifactId", RULE_ARTIFACT_ID);
		editor.appendText(dependency, "version", ruleVersion);
	}

	private void enforceExecution(PomEditor editor, Element executions) {
		Element execution = editor.appendElement(executions, "execution");
		editor.appendText(execution, "id", "enforce-claude-md");
		editor.appendText(execution, "phase", "validate");
		editor.appendText(execution, "inherited", "false");
		Element goals = editor.appendElement(execution, "goals");
		editor.appendText(goals, "goal", "enforce");
		claudeMdConfiguration(editor, execution);
	}

	private void claudeMdConfiguration(PomEditor editor, Element execution) {
		Element configuration = editor.appendElement(execution, "configuration");
		Element rules = editor.appendElement(configuration, "rules");
		Element claudeMdFormat = editor.appendElement(rules, "claudeMdFormat");
		editor.appendText(claudeMdFormat, "claudeMdFile", CLAUDE_MD_FILE);
	}

	private Optional<Element> child(Element parent, String name) {
		return children(parent, name).stream().findFirst();
	}

	private static List<Element> children(Element parent, String name) {
		List<Element> matches = new ArrayList<>();
		NodeList nodes = parent.getChildNodes();
		for (int index = 0; index < nodes.getLength(); index++) {
			addIfMatch(matches, nodes.item(index), name);
		}
		return matches;
	}

	private static void addIfMatch(List<Element> matches, Node node, String name) {
		if (node instanceof Element element && name.equals(element.getLocalName())) {
			matches.add(element);
		}
	}

	private String readText(Path pomFile) {
		try {
			return Files.readString(pomFile);
		} catch (IOException e) {
			throw new AdoptionException("Could not read POM: " + pomFile, e);
		}
	}

	private Document parse(Path pomFile) {
		try {
			return builder().parse(pomFile.toFile());
		} catch (IOException | SAXException e) {
			throw new AdoptionException("Could not read POM: " + pomFile, e);
		}
	}

	private DocumentBuilder builder() {
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

	/**
	 * Writes the document back verbatim. The transformer's own indentation is left
	 * off and the parsed whitespace nodes are kept, so only the elements the edit
	 * added — already indented by {@link PomEditor} — differ from the original. The
	 * original's XML declaration and trailing newline are carried over exactly so
	 * the first and last lines are not disturbed either.
	 */
	private void write(Document document, Path pomFile, String original) {
		try {
			String content = declarationPrefix(original) + transformBody(document);
			String withTrailingNewline = matchTrailingNewline(content, original);
			Files.createDirectories(pomFile.toAbsolutePath().getParent());
			Files.writeString(pomFile, applyLineTerminator(withTrailingNewline, lineTerminator(original)));
		} catch (IOException | TransformerException e) {
			throw new AdoptionException("Could not write POM: " + pomFile, e);
		}
	}

	/**
	 * The line terminator the original file used. XML parsing normalizes {@code \r\n}
	 * to {@code \n}, so the DOM the transformer serializes has lost the original
	 * terminator; capturing it here lets the rewrite keep a CRLF POM on CRLF rather
	 * than silently flipping every line to LF and reformatting the whole file. A file
	 * with no {@code \r\n} is treated as LF.
	 */
	private String lineTerminator(String original) {
		return original.contains("\r\n") ? "\r\n" : "\n";
	}

	/**
	 * Rewrites every line terminator in {@code content} to {@code terminator}. The
	 * assembled content mixes the transformer's LF body, the added block's LF
	 * indentation, and the declaration carried over verbatim from the original, so it
	 * is first normalized to a single form to avoid double-converting the parts that
	 * already ended in the target terminator.
	 */
	private String applyLineTerminator(String content, String terminator) {
		String normalized = content.replace("\r\n", "\n").replace("\r", "\n");
		return terminator.equals("\n") ? normalized : normalized.replace("\n", terminator);
	}

	private String transformBody(Document document) throws TransformerException {
		StringWriter writer = new StringWriter();
		transformer().transform(new DOMSource(document), new StreamResult(writer));
		return writer.toString();
	}

	/**
	 * The XML declaration the original file opened with, up to and including its
	 * line terminator, or empty when it had none. Carrying it over verbatim keeps
	 * the transformer from inventing one (and adding a spurious first-line change)
	 * on a POM that started straight with {@code <project>}.
	 */
	private String declarationPrefix(String original) {
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

	private int lineTerminatorEnd(String text, int from) {
		int index = from;
		if (index < text.length() && text.charAt(index) == '\r') {
			index++;
		}
		if (index < text.length() && text.charAt(index) == '\n') {
			index++;
		}
		return index;
	}

	private String matchTrailingNewline(String content, String original) {
		boolean originalEnds = original.endsWith("\n") || original.endsWith("\r");
		boolean contentEnds = content.endsWith("\n") || content.endsWith("\r");
		return originalEnds && !contentEnds ? content + "\n" : content;
	}

	private Transformer transformer() throws TransformerException {
		TransformerFactory factory = TransformerFactory.newInstance();
		factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
		Transformer transformer = factory.newTransformer();
		transformer.setOutputProperty(OutputKeys.INDENT, "no");
		transformer.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "yes");
		return transformer;
	}

	/**
	 * Appends new elements to a parsed POM without disturbing its existing layout.
	 * Each appended element is preceded by a newline and an indentation matching
	 * the document's own unit (detected from the file, defaulting to two spaces),
	 * and is inserted before the parent's own closing indentation so that closing
	 * tag stays put. Every container is attached to its parent before its children
	 * are added, so an element's depth — and therefore its indentation — is known
	 * as soon as it is appended.
	 */
	private static final class PomEditor {

		private static final String DEFAULT_INDENT_UNIT = "  ";

		private final Document document;
		private final String namespace;
		private final String indentUnit;

		private PomEditor(Document document) {
			this.document = document;
			this.namespace = document.getDocumentElement().getNamespaceURI();
			this.indentUnit = detectIndentUnit(document.getDocumentElement());
		}

		private Element pluginsElement() {
			Element project = document.getDocumentElement();
			Element build = childOrCreate(project, "build");
			return childOrCreate(build, "plugins");
		}

		private Element childOrCreate(Element parent, String name) {
			return firstChild(parent, name).orElseGet(() -> appendElement(parent, name));
		}

		private Element appendElement(Element parent, String name) {
			return appendChild(parent, create(name));
		}

		private void appendText(Element parent, String name, String text) {
			Element element = create(name);
			element.setTextContent(text);
			appendChild(parent, element);
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

		private Optional<Element> firstChild(Element parent, String name) {
			return children(parent, name).stream().findFirst();
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
			NodeList children = root.getChildNodes();
			for (int index = 0; index < children.getLength(); index++) {
				String unit = leadingIndentOf(children.item(index));
				if (!unit.isEmpty()) {
					return unit;
				}
			}
			return DEFAULT_INDENT_UNIT;
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
	}
}
