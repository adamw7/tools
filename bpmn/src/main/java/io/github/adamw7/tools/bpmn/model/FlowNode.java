package io.github.adamw7.tools.bpmn.model;

import java.util.Objects;

/**
 * A single activity, event or gateway inside a {@link Process}. Immutable.
 *
 * @param id   the BPMN element id, unique within its process; never blank
 * @param name the human-readable label, or empty when the element has none
 * @param type the recognised BPMN type of this node
 */
public record FlowNode(String id, String name, FlowNodeType type) {

	public FlowNode {
		Objects.requireNonNull(id, "id");
		Objects.requireNonNull(name, "name");
		Objects.requireNonNull(type, "type");
	}
}
