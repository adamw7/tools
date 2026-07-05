package io.github.adamw7.tools.bpmn.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

class ModelTest {

	@Test
	void flowNodeRejectsNullComponents() {
		assertThrows(NullPointerException.class, () -> new FlowNode(null, "", FlowNodeType.TASK));
		assertThrows(NullPointerException.class, () -> new FlowNode("id", null, FlowNodeType.TASK));
		assertThrows(NullPointerException.class, () -> new FlowNode("id", "", null));
	}

	@Test
	void sequenceFlowRejectsNullComponents() {
		assertThrows(NullPointerException.class, () -> new SequenceFlow(null, "", "a", "b"));
		assertThrows(NullPointerException.class, () -> new SequenceFlow("id", "", "a", null));
	}

	@Test
	void processDefensivelyCopiesItsCollections() {
		List<FlowNode> nodes = new ArrayList<>(List.of(new FlowNode("s", "", FlowNodeType.START_EVENT)));
		Process process = new Process("p", "", nodes, List.of());

		nodes.clear();

		assertEquals(1, process.flowNodes().size());
		assertThrows(UnsupportedOperationException.class,
				() -> process.flowNodes().add(new FlowNode("x", "", FlowNodeType.TASK)));
	}

	@Test
	void findsProcessByIdWithinModel() {
		Process process = new Process("p", "Payments", List.of(), List.of());
		BpmnModel model = new BpmnModel("", List.of(process));

		assertEquals("Payments", model.findProcess("p").orElseThrow().name());
		assertTrue(model.findProcess("absent").isEmpty());
	}
}
