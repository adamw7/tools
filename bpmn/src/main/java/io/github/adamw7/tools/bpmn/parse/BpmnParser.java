package io.github.adamw7.tools.bpmn.parse;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

import io.github.adamw7.tools.bpmn.model.BpmnModel;
import io.github.adamw7.tools.bpmn.model.FlowNode;
import io.github.adamw7.tools.bpmn.model.FlowNodeType;
import io.github.adamw7.tools.bpmn.model.Process;
import io.github.adamw7.tools.bpmn.model.SequenceFlow;

/**
 * Reads a BPMN 2.0 XML document into a {@link BpmnModel} using only the JDK XML
 * APIs. The parser is namespace-aware, hardened against XML external entity
 * (XXE) attacks, and ignores elements it does not recognise (documentation,
 * lane sets, diagram interchange, and so on).
 */
public final class BpmnParser {

	private static final String DEFINITIONS = "definitions";
	private static final String PROCESS = "process";
	private static final String SEQUENCE_FLOW = "sequenceFlow";

	/** Parses BPMN XML read from the given stream. The stream is not closed. */
	public BpmnModel parse(InputStream bpmnXml) {
		Objects.requireNonNull(bpmnXml, "bpmnXml");
		return toModel(buildDocument(bpmnXml));
	}

	/** Parses the BPMN file at the given path. */
	public BpmnModel parse(Path bpmnFile) {
		Objects.requireNonNull(bpmnFile, "bpmnFile");
		try (InputStream stream = Files.newInputStream(bpmnFile)) {
			return parse(stream);
		} catch (IOException e) {
			throw new BpmnParseException("Cannot read BPMN file: " + bpmnFile, e);
		}
	}

	/** Parses BPMN XML held in the given string. */
	public BpmnModel parse(String bpmnXml) {
		Objects.requireNonNull(bpmnXml, "bpmnXml");
		return parse(new ByteArrayInputStream(bpmnXml.getBytes(StandardCharsets.UTF_8)));
	}

	private Document buildDocument(InputStream bpmnXml) {
		try {
			return newSecureBuilder().parse(bpmnXml);
		} catch (SAXException | IOException e) {
			throw new BpmnParseException("Malformed BPMN XML", e);
		} catch (ParserConfigurationException e) {
			throw new BpmnParseException("Cannot configure the XML parser", e);
		}
	}

	private DocumentBuilder newSecureBuilder() throws ParserConfigurationException {
		DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
		factory.setNamespaceAware(true);
		factory.setExpandEntityReferences(false);
		factory.setXIncludeAware(false);
		harden(factory);
		return factory.newDocumentBuilder();
	}

	private void harden(DocumentBuilderFactory factory) throws ParserConfigurationException {
		factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
		factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
		factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
		factory.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
	}

	private BpmnModel toModel(Document document) {
		Element definitions = document.getDocumentElement();
		requireDefinitions(definitions);
		List<Process> processes = childElements(definitions).stream()
				.filter(element -> isNamed(element, PROCESS))
				.map(this::toProcess)
				.toList();
		return new BpmnModel(attr(definitions, "targetNamespace"), processes);
	}

	private void requireDefinitions(Element root) {
		if (root == null || !isNamed(root, DEFINITIONS)) {
			throw new BpmnParseException("Root element must be <definitions>, but was: " + describe(root));
		}
	}

	private String describe(Element root) {
		return root == null ? "<empty document>" : "<" + localName(root) + ">";
	}

	private Process toProcess(Element processElement) {
		List<FlowNode> nodes = new ArrayList<>();
		List<SequenceFlow> flows = new ArrayList<>();
		childElements(processElement).forEach(child -> collect(child, nodes, flows));
		return new Process(attr(processElement, "id"), attr(processElement, "name"), nodes, flows);
	}

	private void collect(Element element, List<FlowNode> nodes, List<SequenceFlow> flows) {
		if (isNamed(element, SEQUENCE_FLOW)) {
			flows.add(toSequenceFlow(element));
		} else {
			FlowNodeType.fromElementName(localName(element))
					.ifPresent(type -> nodes.add(toFlowNode(element, type)));
		}
	}

	private FlowNode toFlowNode(Element element, FlowNodeType type) {
		return new FlowNode(attr(element, "id"), attr(element, "name"), type);
	}

	private SequenceFlow toSequenceFlow(Element element) {
		return new SequenceFlow(attr(element, "id"), attr(element, "name"),
				attr(element, "sourceRef"), attr(element, "targetRef"));
	}

	private List<Element> childElements(Element parent) {
		List<Element> elements = new ArrayList<>();
		NodeList children = parent.getChildNodes();
		java.util.stream.IntStream.range(0, children.getLength())
				.mapToObj(children::item)
				.filter(node -> node.getNodeType() == Node.ELEMENT_NODE)
				.map(Element.class::cast)
				.forEach(elements::add);
		return elements;
	}

	private boolean isNamed(Element element, String localName) {
		return localName.equals(localName(element));
	}

	private String localName(Element element) {
		return Optional.ofNullable(element.getLocalName()).orElseGet(element::getNodeName);
	}

	private String attr(Element element, String name) {
		return element.getAttribute(name);
	}
}
