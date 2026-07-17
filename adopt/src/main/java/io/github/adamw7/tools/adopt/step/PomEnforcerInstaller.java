package io.github.adamw7.tools.adopt.step;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

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
 * namespace, and is idempotent: a POM that already declares the enforcer plugin
 * is left untouched.
 */
public class PomEnforcerInstaller {

	static final String ENFORCER_GROUP_ID = "org.apache.maven.plugins";
	static final String ENFORCER_ARTIFACT_ID = "maven-enforcer-plugin";
	static final String RULE_ARTIFACT_ID = "tools.claude-code-enforcer";
	static final String RULE_GROUP_ID = "io.github.adamw7";
	static final String DEFAULT_RULE_VERSION = "2.5.0";

	private final String ruleVersion;

	public PomEnforcerInstaller() {
		this(DEFAULT_RULE_VERSION);
	}

	public PomEnforcerInstaller(String ruleVersion) {
		this.ruleVersion = ruleVersion;
	}

	/**
	 * @return {@code true} when the plugin was added, {@code false} when the POM
	 *         already declared it and was left unchanged.
	 */
	public boolean install(Path pomFile) {
		Document document = read(pomFile);
		Element project = document.getDocumentElement();
		Element plugins = pluginsElement(document, project);
		if (findEnforcerPlugin(plugins).isPresent()) {
			return false;
		}
		plugins.appendChild(enforcerPlugin(document));
		write(document, pomFile);
		return true;
	}

	private Element pluginsElement(Document document, Element project) {
		Element build = childOrCreate(document, project, "build");
		return childOrCreate(document, build, "plugins");
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

	private Element enforcerPlugin(Document document) {
		Element plugin = create(document, "plugin");
		appendText(document, plugin, "groupId", ENFORCER_GROUP_ID);
		appendText(document, plugin, "artifactId", ENFORCER_ARTIFACT_ID);
		plugin.appendChild(ruleDependencies(document));
		plugin.appendChild(enforceExecutions(document));
		return plugin;
	}

	private Element ruleDependencies(Document document) {
		Element dependencies = create(document, "dependencies");
		Element dependency = create(document, "dependency");
		appendText(document, dependency, "groupId", RULE_GROUP_ID);
		appendText(document, dependency, "artifactId", RULE_ARTIFACT_ID);
		appendText(document, dependency, "version", ruleVersion);
		dependencies.appendChild(dependency);
		return dependencies;
	}

	private Element enforceExecutions(Document document) {
		Element executions = create(document, "executions");
		Element execution = create(document, "execution");
		appendText(document, execution, "id", "enforce-claude-md");
		appendText(document, execution, "phase", "validate");
		Element goals = create(document, "goals");
		appendText(document, goals, "goal", "enforce");
		execution.appendChild(goals);
		execution.appendChild(claudeMdConfiguration(document));
		executions.appendChild(execution);
		return executions;
	}

	private Element claudeMdConfiguration(Document document) {
		Element configuration = create(document, "configuration");
		Element rules = create(document, "rules");
		rules.appendChild(create(document, "claudeMdFormat"));
		configuration.appendChild(rules);
		return configuration;
	}

	private Element childOrCreate(Document document, Element parent, String name) {
		return child(parent, name).orElseGet(() -> appendChild(document, parent, name));
	}

	private Element appendChild(Document document, Element parent, String name) {
		Element element = create(document, name);
		parent.appendChild(element);
		return element;
	}

	private void appendText(Document document, Element parent, String name, String text) {
		Element element = create(document, name);
		element.setTextContent(text);
		parent.appendChild(element);
	}

	private Element create(Document document, String name) {
		String namespace = document.getDocumentElement().getNamespaceURI();
		return namespace == null ? document.createElement(name) : document.createElementNS(namespace, name);
	}

	private Optional<Element> child(Element parent, String name) {
		return children(parent, name).stream().findFirst();
	}

	private List<Element> children(Element parent, String name) {
		List<Element> matches = new ArrayList<>();
		NodeList nodes = parent.getChildNodes();
		for (int index = 0; index < nodes.getLength(); index++) {
			addIfMatch(matches, nodes.item(index), name);
		}
		return matches;
	}

	private void addIfMatch(List<Element> matches, Node node, String name) {
		if (node instanceof Element element && name.equals(element.getLocalName())) {
			matches.add(element);
		}
	}

	private Document read(Path pomFile) {
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

	private void write(Document document, Path pomFile) {
		try {
			stripWhitespace(document.getDocumentElement());
			Files.createDirectories(pomFile.toAbsolutePath().getParent());
			transformer().transform(new DOMSource(document), new StreamResult(pomFile.toFile()));
		} catch (IOException | TransformerException e) {
			throw new AdoptionException("Could not write POM: " + pomFile, e);
		}
	}

	/**
	 * Removes whitespace-only text nodes so the transformer can re-indent the
	 * document cleanly instead of interleaving the original whitespace with
	 * fresh indentation. Safe for a POM because it carries no mixed content.
	 */
	private void stripWhitespace(Node node) {
		NodeList children = node.getChildNodes();
		List<Node> blankTextNodes = new ArrayList<>();
		for (int index = 0; index < children.getLength(); index++) {
			collectBlankText(children.item(index), blankTextNodes);
		}
		blankTextNodes.forEach(node::removeChild);
	}

	private void collectBlankText(Node child, List<Node> blankTextNodes) {
		if (child.getNodeType() == Node.TEXT_NODE && child.getTextContent().isBlank()) {
			blankTextNodes.add(child);
		} else if (child.getNodeType() == Node.ELEMENT_NODE) {
			stripWhitespace(child);
		}
	}

	private Transformer transformer() throws TransformerException {
		TransformerFactory factory = TransformerFactory.newInstance();
		factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
		Transformer transformer = factory.newTransformer();
		transformer.setOutputProperty(OutputKeys.INDENT, "yes");
		transformer.setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "2");
		return transformer;
	}
}
