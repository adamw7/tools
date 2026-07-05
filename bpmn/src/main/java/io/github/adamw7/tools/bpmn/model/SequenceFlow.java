package io.github.adamw7.tools.bpmn.model;

import java.util.Objects;

/**
 * A directed connection between two {@link FlowNode}s within a {@link Process}.
 * Immutable.
 *
 * @param id        the BPMN element id of the flow; never blank
 * @param name      the human-readable label, or empty when the flow has none
 * @param sourceRef the id of the node the flow leaves
 * @param targetRef the id of the node the flow enters
 */
public record SequenceFlow(String id, String name, String sourceRef, String targetRef) {

	public SequenceFlow {
		Objects.requireNonNull(id, "id");
		Objects.requireNonNull(name, "name");
		Objects.requireNonNull(sourceRef, "sourceRef");
		Objects.requireNonNull(targetRef, "targetRef");
	}
}
