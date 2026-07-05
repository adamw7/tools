package io.github.adamw7.tools.bpmn.model;

import java.util.Optional;

/**
 * The kinds of BPMN 2.0 flow node this parser recognises, each bound to its
 * BPMN XML element local name and its broad {@link Category}.
 *
 * <p>Sequence flows are edges rather than nodes and are therefore modelled by
 * {@link SequenceFlow}, not by this enum.
 */
public enum FlowNodeType {

	START_EVENT("startEvent", Category.EVENT),
	END_EVENT("endEvent", Category.EVENT),
	INTERMEDIATE_CATCH_EVENT("intermediateCatchEvent", Category.EVENT),
	INTERMEDIATE_THROW_EVENT("intermediateThrowEvent", Category.EVENT),
	BOUNDARY_EVENT("boundaryEvent", Category.EVENT),

	TASK("task", Category.ACTIVITY),
	USER_TASK("userTask", Category.ACTIVITY),
	SERVICE_TASK("serviceTask", Category.ACTIVITY),
	SCRIPT_TASK("scriptTask", Category.ACTIVITY),
	MANUAL_TASK("manualTask", Category.ACTIVITY),
	BUSINESS_RULE_TASK("businessRuleTask", Category.ACTIVITY),
	SEND_TASK("sendTask", Category.ACTIVITY),
	RECEIVE_TASK("receiveTask", Category.ACTIVITY),
	CALL_ACTIVITY("callActivity", Category.ACTIVITY),
	SUB_PROCESS("subProcess", Category.ACTIVITY),

	EXCLUSIVE_GATEWAY("exclusiveGateway", Category.GATEWAY),
	PARALLEL_GATEWAY("parallelGateway", Category.GATEWAY),
	INCLUSIVE_GATEWAY("inclusiveGateway", Category.GATEWAY),
	EVENT_BASED_GATEWAY("eventBasedGateway", Category.GATEWAY),
	COMPLEX_GATEWAY("complexGateway", Category.GATEWAY);

	/** Broad grouping shared by several concrete node types. */
	public enum Category {
		EVENT, ACTIVITY, GATEWAY
	}

	private final String elementName;
	private final Category category;

	FlowNodeType(String elementName, Category category) {
		this.elementName = elementName;
		this.category = category;
	}

	/** The BPMN XML element local name that maps to this type. */
	public String elementName() {
		return elementName;
	}

	public Category category() {
		return category;
	}

	public boolean isEvent() {
		return category == Category.EVENT;
	}

	public boolean isActivity() {
		return category == Category.ACTIVITY;
	}

	public boolean isGateway() {
		return category == Category.GATEWAY;
	}

	/**
	 * Resolves the flow node type for a BPMN element local name.
	 *
	 * @param elementName the XML element local name, e.g. {@code "userTask"}
	 * @return the matching type, or empty when the element is not a recognised
	 *         flow node
	 */
	public static Optional<FlowNodeType> fromElementName(String elementName) {
		return java.util.Arrays.stream(values())
				.filter(type -> type.elementName.equals(elementName))
				.findFirst();
	}
}
