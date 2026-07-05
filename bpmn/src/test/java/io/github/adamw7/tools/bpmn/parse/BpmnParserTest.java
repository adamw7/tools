package io.github.adamw7.tools.bpmn.parse;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.InputStream;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;

import io.github.adamw7.tools.bpmn.model.BpmnModel;
import io.github.adamw7.tools.bpmn.model.FlowNodeType;
import io.github.adamw7.tools.bpmn.model.Process;

class BpmnParserTest {

	private final BpmnParser parser = new BpmnParser();

	private BpmnModel parseResource() {
		try (InputStream stream = getClass().getResourceAsStream("/order-process.bpmn")) {
			return parser.parse(stream);
		} catch (Exception e) {
			throw new IllegalStateException(e);
		}
	}

	@Test
	void readsTargetNamespaceAndSingleProcess() {
		BpmnModel model = parseResource();

		assertEquals("http://example.com/orders", model.targetNamespace());
		assertEquals(1, model.processes().size());
		assertEquals("orderProcess", model.processes().get(0).id());
		assertEquals("Order handling", model.processes().get(0).name());
	}

	@Test
	void readsEveryFlowNodeWithItsType() {
		Process process = parseResource().processes().get(0);

		assertEquals(6, process.flowNodes().size());
		assertEquals(FlowNodeType.START_EVENT, process.findNode("start").orElseThrow().type());
		assertEquals(FlowNodeType.USER_TASK, process.findNode("review").orElseThrow().type());
		assertEquals(FlowNodeType.EXCLUSIVE_GATEWAY, process.findNode("approved").orElseThrow().type());
		assertEquals(FlowNodeType.SERVICE_TASK, process.findNode("ship").orElseThrow().type());
		assertEquals(2, process.nodesOfType(FlowNodeType.END_EVENT).size());
	}

	@Test
	void readsSequenceFlowsWithEndsAndNames() {
		Process process = parseResource().processes().get(0);

		assertEquals(5, process.sequenceFlows().size());
		assertEquals(2, process.outgoing("approved").size());
		assertEquals("yes", process.sequenceFlows().get(2).name());
		assertEquals("approved", process.incoming("ship").get(0).sourceRef());
	}

	@Test
	void leavesNameEmptyWhenAbsent() {
		Process process = parseResource().processes().get(0);

		assertEquals("", process.sequenceFlows().get(0).name());
	}

	@Test
	void ignoresUnrecognisedElementsSuchAsDiagram() {
		Process process = parseResource().processes().get(0);

		assertTrue(process.findNode("diagram").isEmpty());
	}

	@Test
	void parsesFromStringToo() {
		String xml = """
				<definitions xmlns="http://www.omg.org/spec/BPMN/20100524/MODEL">
				  <process id="p">
				    <startEvent id="s"/>
				    <endEvent id="e"/>
				    <sequenceFlow id="f" sourceRef="s" targetRef="e"/>
				  </process>
				</definitions>
				""";

		BpmnModel model = parser.parse(xml);

		assertEquals(1, model.processes().size());
		assertEquals("", model.targetNamespace());
		assertEquals(2, model.processes().get(0).flowNodes().size());
	}

	@Test
	void rejectsMalformedXml() {
		assertThrows(BpmnParseException.class, () -> parser.parse("<definitions><process></definitions>"));
	}

	@Test
	void rejectsWrongRootElement() {
		BpmnParseException thrown = assertThrows(BpmnParseException.class,
				() -> parser.parse("<notDefinitions/>"));
		assertTrue(thrown.getMessage().contains("definitions"));
	}

	@Test
	void rejectsDocumentTypeDeclarationToBlockXxe() {
		String withDoctype = "<?xml version=\"1.0\"?><!DOCTYPE foo><definitions/>";

		assertThrows(BpmnParseException.class, () -> parser.parse(withDoctype));
	}

	@Test
	void reportsMissingFile() {
		Path missing = Path.of("does-not-exist.bpmn");

		assertThrows(BpmnParseException.class, () -> parser.parse(missing));
	}
}
