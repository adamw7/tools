package io.github.adamw7.tools.bpmn.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class FlowNodeTypeTest {

	@ParameterizedTest
	@CsvSource({
			"startEvent,START_EVENT",
			"userTask,USER_TASK",
			"serviceTask,SERVICE_TASK",
			"exclusiveGateway,EXCLUSIVE_GATEWAY",
			"callActivity,CALL_ACTIVITY"
	})
	void resolvesKnownElementNames(String elementName, FlowNodeType expected) {
		assertEquals(Optional.of(expected), FlowNodeType.fromElementName(elementName));
	}

	@Test
	void returnsEmptyForUnknownElementName() {
		assertTrue(FlowNodeType.fromElementName("sequenceFlow").isEmpty());
		assertTrue(FlowNodeType.fromElementName("laneSet").isEmpty());
	}

	@Test
	void classifiesCategories() {
		assertTrue(FlowNodeType.START_EVENT.isEvent());
		assertFalse(FlowNodeType.START_EVENT.isGateway());
		assertTrue(FlowNodeType.USER_TASK.isActivity());
		assertTrue(FlowNodeType.PARALLEL_GATEWAY.isGateway());
		assertEquals(FlowNodeType.Category.GATEWAY, FlowNodeType.PARALLEL_GATEWAY.category());
	}

	@Test
	void everyTypeRoundTripsThroughItsElementName() {
		java.util.Arrays.stream(FlowNodeType.values()).forEach(type ->
				assertEquals(Optional.of(type), FlowNodeType.fromElementName(type.elementName())));
	}
}
